package worth;

import java.rmi.server.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import myExceptions.EmptyPasswordException;
import myExceptions.EmptyUserException;
import myExceptions.UserAlreadyRegisteredException;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.*;

import myInterfaces.CallbackServerInterface;
import myInterfaces.CallbackUserInterface;
import myInterfaces.RMIUserInterface;

public class Server implements Runnable, RMIUserInterface {

	//Abilita o disabilita la modalità debug
	private static boolean DEBUG = false;
	
	//Parametri e variabili utilizzati localmente dal server
	private User[] registeredUsers;	//Contiene tutti gli utenti registrati
	private User[] onlineUsers;	//Contiene tutti gli utenti online
	private Project[] projects;	//Contiene tutti i progetti creati
	private int chatPort = 4444;	//La porta su cui aprire tutte le chat dei progetti
	
	
	//Parametri e variabili per i file utilizzati dal server
	private String usersFolder = "."+File.separator+"users";
	private String usersFile = usersFolder+File.separator+"members.json";	//File che contiene gli utenti registrati
	private String projectsFolder = "."+File.separator+"projects";	//Cartella per i progetti degli utenti
	
	//Parametri e variabili per NIO Multiplexing
	private int serverPort = 7777;
	private ServerSocketChannel serverChannel;
	private Selector selector;	//Selector per multiplexing
	private ServerSocket socket;	//Socket per comunicare con i client
	private Map<SocketChannel, StringBuilder> sessions;	//Contiene tutte le sessioni aperte con i client
	
	//Parametri e variabili per RMI e RMI callback
	private static Registry RMIRegistry;
	private static RMIUserImpl rmiimpl;
	private int RMIServicePort = 1234;
	private List<CallbackUserInterface> callbackUsers;	//Contiene tutti i client registrati alle callback
	private String RMIServiceName = "RMIService";
	private String RMICallbackServiceName = "RMICallbackService";
	private Map<String, Boolean> users;	//Contiene tutti gli utenti registrati e il loro status, usato per callback ad un client
	
	
	//Parametri e variabili per Multicast
	private String firstfreeaddress;	//Salva il primo indirizzo libero per una chat multicast
	
	/**
	 * Costruttore per la classe server, invocato dal main
	 */
	public Server() {
		
		initVariables();
		
	}
	
	/**
	 * metodo chiamato per inizializzare le variabili locali del server
	 */
	private void initVariables() {
		
		this.registeredUsers = new User[0];	//Nuovo array vuoto
		
		this.onlineUsers = new User[0];	//Nuovo array vuoto
		
		this.projects = new Project[0];	//Nuovo array vuoto
		
		this.sessions = new HashMap<SocketChannel, StringBuilder>();
		
		this.callbackUsers = new ArrayList<CallbackUserInterface>();
		
		this.users = new HashMap<String, Boolean>();
		
	}
	
	/**
	 * metodo chiamato dal main per avviare il thread Server
	 */
	public void run() {
		
		boot();	//Crea i file e le cartelle da salvare se non esistono, altrimenti ripristina l'ultimo stato del sistema
		
		initRMIService();	//Inizializza il servizio RMI
		
		try {
			initMultiplexing();	//Inizializza il servizio NIO Multiplexing
		} catch (IOException e) {
			if(DEBUG) System.err.println("Failed to initialize NIO Multiplexing service");
			e.printStackTrace();
			System.exit(0);	//Termina il server
		}	
		
		initCallbackService();	//Inizializza il servizio RMI Callback
		
		//Server Loop: gestisce finchè non terminato tutte le richieste TCP dei client tramite NIO Multiplexing
		while(true) {
			if(this.selector.isOpen()) {
				try {
					int keys = selector.select();
					
					if(keys>0) handleKeys(this.serverChannel, selector.selectedKeys());	//Gestisce le richieste TCP dei client
					
				} catch (IOException e) {
					if(DEBUG) System.err.println("Failed to retrieve NIO Multiplexing Selector keys");
					e.printStackTrace();
					System.exit(0);	//Termina il server
				}
				
			}
		}
		
	}
	
	/**
	 * metodo chiamato per inizializzare le variabili del server,
	 * gli utenti registrati vengono ricercati in formato JSON sul file this.usersFile
	 * che, se non esiste, viene creato
	 * le liste vengono ricercate nelle relative cartelle progetto:
	 * se nessuna lista viene trovata, vengono create le quattro cartelle
	 * per le liste todo, inprogress, toberevised e done
	 * le card vengono ricercate nelle rispettive cartelle liste interne ad
	 * un progetto
	 */
	private void boot() {
		
		File f = new File(this.usersFile);
		
		if(f.exists()) {
			//Esiste il file con utenti registrati, legge ed inizializza le variabili del server
			
			assert f.exists();	//Assicura che il file esiste
			
			loadUsers();	//Legge gli utenti salvati
			
			File projectsdir = new File(this.projectsFolder);
			
			if(!projectsdir.exists()) {	//Controlla che esista la cartella projects
				if(!projectsdir.mkdir()) {
					//Errore nella creazione della cartella progetti
					if(DEBUG) System.err.println("Boot error: can't create projects folder "+projectsdir);
					System.exit(0);	//Termina il server
				}
			}
			else {
				loadProjects();	//Legge i progetti salvati
				
				loadCards();	//Legge le card salvate
			}
		}
		else {
			//Non e' stato trovato il file con utenti registrati, lo crea
			
			File usersdir = new File(this.usersFolder);	//Crea la cartella per il file utenti
			
			if(!usersdir.exists()) {
				if(!usersdir.mkdir()) {
					//Errore nella creazione della cartella utenti
					if(DEBUG) System.err.println("Boot error: can't create users folder "+usersdir);
					System.exit(0);	//Termina il server
				}
			}
			
			try {
				
				f.createNewFile();	//Crea il file JSON degli utenti
			} catch (IOException e) {
				if(DEBUG) System.err.println("Boot error: can't create user file "+f);
				System.exit(0);	//Termina il server
			}
			
			File projectsdir = new File(this.projectsFolder);
			
			if(!projectsdir.exists()) {	//Controlla che esista la cartella projects
				if(!projectsdir.mkdir()) {
					//Errore nella creazione della cartella progetti
					if(DEBUG) System.err.println("Boot error: can't create projects folder "+projectsdir);
					System.exit(0);	//Termina il server
				}
			}
			
		}
		
		if(DEBUG) System.out.println("Boot complete");
		
		System.out.println("Server WORTH Online");
		
	}
	
	/**
	 * metodo chiamato ad un ripristino del server, legge dal file this.usersFile gli
	 * utenti registrati in formato JSON e li salva nelle variabili locali
	 */
	private void loadUsers() {

		User[] utenti = null;
		
		Path path = Paths.get(this.usersFile);
		
		byte[] bytes = null;
		
		try {
			bytes = Files.readAllBytes(path);
		} catch (IOException e) {
			if(DEBUG) System.err.println("Errore in avvio: fallita lettura su file"+this.usersFile);
			System.exit(0); //Termina il server
		}
		
		if(bytes.length==0) {
			//Il file utenti letto e' vuoto, non proseguo il parsing del file JSON
			if(DEBUG) System.out.println("Users file empty "+this.usersFile+", skipping JSON parsing");
			return;
		}
		
		ObjectMapper mapper = new ObjectMapper();
		
		ObjectReader reader = mapper.readerFor(User[].class);
		
		try {
			utenti = reader.readValue(bytes);
		} catch (IOException e) {
			if(DEBUG) System.err.println("Errore in avvio: fallita conversione oggetti JSON su file"+this.usersFile);
			System.exit(0); //Termina il server
		}
		
		this.registeredUsers = utenti;
		
		//Al nuovo avvio, tutti gli utenti risulteranno offline in quanto nessuno ha ancora effettuato il login
		for(User u : this.registeredUsers) {
			this.users.put(u.getUsername(), false);
		}
		
		if(DEBUG) System.out.println("Users found and loaded correctly from file"+this.usersFile);
		
	}

	/**
	 * metodo chiamato ad un ripristino del server, legge il contenuto della cartella this.projectsFolder
	 * e, per ogni sottocartella presente ricerca al suo interno un file project.json per inizializzare
	 * tutti i progetti localmente
	 * attiva poi il servizio di chat per ogni progetto caricato correttamente
	 */
	private void loadProjects() {
		
		File projectsfolder = new File(this.projectsFolder);
		
		if(projectsfolder.list().length==0) {	//Cartella progetti vuota
			if(DEBUG) System.err.println("Projects folder "+this.projectsFolder+" empty, can't load any project");
			return;
		}
		
		ArrayList<Project> progetti = new ArrayList<Project>();
		
		//Analizza tutti i file nella cartella
		for(File projectfolder : projectsfolder.listFiles()) {
			if(projectfolder.isDirectory()) {	//Il file analizzato è una directory
				
				String[] files = projectfolder.list();
				
				for(String file : files) {
					if(file.equalsIgnoreCase("project.json")) {	//File progetto json trovato
						
						Path path = Paths.get(projectfolder+File.separator+file);
						
						byte[] bytes = null;
						
						try {
							bytes = Files.readAllBytes(path);
						} catch (IOException e) {
							if(DEBUG) System.err.println("Errore in avvio: fallita lettura su progetto "+path+", non verrà aggiunto ai progetti attivi");
							break;
						}
						
						if(bytes.length==0) {
							//Il file progetto letto e' vuoto, non proseguo il parsing del file JSON
							if(DEBUG) System.out.println("Project file empty "+path+", skipping it");
							break;
						}
						
						ObjectMapper mapper = new ObjectMapper();
						
						ObjectReader reader = mapper.readerFor(Project.class);
						
						Project progetto = null;
						
						try {
							progetto = reader.readValue(bytes);
						} catch (IOException e) {
							if(DEBUG) System.err.println("Errore in avvio: fallita conversione oggetto JSON su file "+path+", non verrà aggiunto ai progetti attivi");
							e.printStackTrace();
							break;
						}
						
						progetti.add(progetto);	//Salva il progetto analizzato
					}
				}
			}
			
			this.projects = progetti.toArray(this.projects);	//Salva i progetti analizzati localmente
		}
		
		//Avvia il servizio chat per tutti i progetti letti
		int n1 = 224, n2 = 0, n3 = 0, n4 = 2;	//Parte da 224.0.0.2
		int i = 0, j = 0, k = 0, l = 0;
		for(Project p : getProjects()) {
			if(i<255) {
				p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
				i++;
			}
			else if(j<255) {
				j++;
				i=0;
				p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
			}
			else if(k<255) {
				k++;
				i=0;
				j=0;
				p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
			}
			else if(l<239) {
				l++;
				i=0;
				j=0;
				k=0;
				p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
			}
			else {
				if(DEBUG) System.err.println("Error: Can't open chat service for project "+p.getProjectName()+", no more multicast addresses");
				return;
			}
		}
		
		//Salva il primo indirizzo disponibile
		this.firstfreeaddress = String.valueOf(n1+l)+" "+String.valueOf(n2+k)+" "+String.valueOf(n3+j)+" "+String.valueOf(n4+i+1);
		
		if(DEBUG) System.out.println("Projects found and loaded correctly from directory "+this.projectsFolder);
		
	}

	/**
	 * metodo chiamato ad un ripristino del server, per ogni progetto caricato
	 * analizza la cartella associata ricercando tutti i file .json con nome
	 * diverso da "project".json e, per ogni file trovato, effettua il parsing
	 * e la serializzazione in un oggetto card
	 * aggiorna quindi il file project.json inserendo ogni card caricata nella
	 * lista corretta (ultimo stato salvato della card)
	 */
	private void loadCards() {

		if(this.projects.length==0) {	//Non sono stati caricati progetti
			if(DEBUG) System.out.println("No projects loaded, skipping card loading");
			return;
		}
		
		ArrayList<Project> projects = new ArrayList<Project>(this.projects.length);
		
		for(Project p : this.projects) {
			//Legge tutte le card nella cartella del progetto corrente
			File projectfolder = new File(this.projectsFolder+File.separator+p.getProjectName());
			
			assert(projectfolder.isDirectory());	//Assicura che il file sia una directory
			
			ArrayList<Card> cards = new ArrayList<Card>();	//Lista delle card caricate
			
			for(File cardfile : projectfolder.listFiles()) {
				
				assert(cardfile.isFile());	//Assicura che si sta analizzando un file
				
				if(cardfile.getName().equalsIgnoreCase("project.json")) {	//Salto analisi del file project.json
					continue;
				}
				
				Path path = Paths.get(projectfolder+File.separator+cardfile.getName());
				
				byte[] bytes;
				
				try {
					bytes = Files.readAllBytes(path);
				} catch (IOException e) {
					if(DEBUG) System.err.println("Errore in avvio: fallita lettura su card "+path+", non verrà aggiunta al sistema");
					continue;
				}
				
				if(bytes.length==0) {
					//Il file card letto e' vuoto, non proseguo il parsing del file JSON
					if(DEBUG) System.out.println("Card file empty "+path+", skipping it");
					continue;
				}
				
				ObjectMapper mapper = new ObjectMapper();
				
				ObjectReader reader = mapper.readerFor(Card.class);
				
				Card newcard = null;
				
				try {
					newcard = reader.readValue(bytes);
				} catch (IOException e) {
					if(DEBUG) System.err.println("Errore in avvio: fallita conversione oggetto JSON su file "+path+", non verrà aggiunta alle card");
					e.printStackTrace();
					continue;
				}
				
				cards.add(newcard);	//Aggiunge la nuova card alla lista locale delle card
			}
			
			if(cards.size()==0) {	//Non sono state trovate card nel progetto
				if(DEBUG) System.out.println("Can't retrieve any card for project "+p.getProjectName());
			}
			//Posiziona le card nelle liste corrette e aggiorna il file project.json
			ArrayList<Card> todoList = new ArrayList<Card>();
			ArrayList<Card> inprogressList = new ArrayList<Card>();
			ArrayList<Card> toberevisedList = new ArrayList<Card>();
			ArrayList<Card> doneList = new ArrayList<Card>();
			
			for(Card c : cards) {
				if(c.getCurrentList().equalsIgnoreCase("TODO")) {
					todoList.add(c);
				}
				else if(c.getCurrentList().equalsIgnoreCase("INPROGRESS")) {
					inprogressList.add(c);
				}
				else if(c.getCurrentList().equalsIgnoreCase("TOBEREVISED")) {
					toberevisedList.add(c);
				}
				else if(c.getCurrentList().equalsIgnoreCase("DONE")) {
					doneList.add(c);
				}
				else {
					if(DEBUG) System.out.println("Card "+c.getName()+" on project "+p.getProjectName()+" has no valid list field, skipping it");
					continue;
				}
			}
			//Tutte le card sono state smistate, aggiorna variabili locali del progetto e il file project.json
			p.setTodoList(todoList);
			p.setInprogressList(inprogressList);
			p.setToberevisedList(toberevisedList);
			p.setDoneList(doneList);
			
			File projectfile = new File(projectfolder+File.separator+"project.json");
			
			assert(projectfile.exists());	//Assicura che il file esiste
			
			ObjectMapper mapper = new ObjectMapper();
			
			ObjectWriter writer = mapper.writerFor(Project.class);
			
			writer = mapper.writer(new DefaultPrettyPrinter());
			
			try {
				writer.writeValue(projectfile, p);
			} catch (IOException e) {
				if(DEBUG) System.err.println("Errore in avvio: fallita conversione oggetto JSON su file "+projectfile+", non verrà aggiunto ai progetti attivi");
				e.printStackTrace();
				continue;
			}
			
			projects.add(p);
		}
		
		//Aggiorna localmente i progetti
		for(Project p : this.projects) {
			for(Project newproject : projects) {
				if(p.getProjectName().equals(newproject.getProjectName())) {	//Progetto da aggiornare
					
					ArrayList<Project> newlist = new ArrayList<Project>(this.projects.length);
					
					newlist.addAll(getProjects());
					
					newlist.remove(p);
					
					newlist.add(newproject);
					
					this.projects = newlist.toArray(this.projects);
				}
			}
		}
		
		if(DEBUG) System.out.println("Card loading complete");
		
	}

	/**
	 * metodo chiamato per inizializzare il servizio RMI con
	 * nome = this.RMIServiceName e porta = this.RMIServicePort
	 */
	private void initRMIService() {
		
		Server.rmiimpl = new RMIUserImpl(this);
		
		try {
			
			Registry r = LocateRegistry.createRegistry(this.RMIServicePort);
			
			RMIUserInterface rmiinterface = 
					(RMIUserInterface) UnicastRemoteObject.exportObject(rmiimpl, 0);
			
			r.rebind(this.RMIServiceName, rmiinterface);
			
			Server.RMIRegistry = r;
			
			if(DEBUG) System.out.println("RMIService succesfully initialized");
		} catch (RemoteException e) {
			System.err.println("Errore nell'inizializzazione del servizio RMI");
			e.printStackTrace();
			System.exit(0);
		}
	}
	
	/**
	 * metodo chiamato per inizializzare il servizio NIO Multiplexing
	 * per la gestione delle richieste TCP dei client, utilizzando
	 * this.serverPort come porta associata al canale this.serverChannel
	 * che utilizza this.selector come selettore e this.socket come socket
	 * viene subito impostata la SelectionKey del selettore in modalita' accept,
	 * così da poter ricevere subito nuove connessioni
	 */
	private void initMultiplexing() throws IOException {
		
		this.serverChannel = ServerSocketChannel.open();
		this.selector = Selector.open();
		this.socket = serverChannel.socket();
		this.serverChannel.configureBlocking(false);
		this.socket.bind(new InetSocketAddress(this.serverPort));
		this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		if(DEBUG) System.out.println("NIO Multiplexing service Online on port "+this.serverPort);
		
	}
	
	/**
	 * metodo chiamato per inizializzare il servizio RMI Callback con
	 * nome = this.RMICallbackServiceName e porta = this.RMICallbackServicePort
	 */
	private void initCallbackService() {
		
		CallbackServerImpl callbackimpl = new CallbackServerImpl(this);
		
		try {
			
			CallbackServerInterface callbackinterface = 
					(CallbackServerInterface) UnicastRemoteObject.exportObject(callbackimpl, 0);
			
			Server.RMIRegistry.rebind(this.RMICallbackServiceName, callbackinterface);
			
			if(DEBUG) System.out.println("RMICallbackService succesfully initialized");
		} catch (RemoteException e) {
			System.err.println("Errore nell'inizializzazione del servizio RMI Callback");
			e.printStackTrace();
			System.exit(0); //Termina il server
		}
		
	}

	/**
	 * Gestore delle chiavi
	 * metodo chiamato nel Server Loop per gestire le richieste TCP dei client
	 * utilizzando NIO Multiplexing
	 * @param channel il canale da analizzare
	 * @param keys le chiavi da gestire
	 */
	private void handleKeys(ServerSocketChannel channel, Set<SelectionKey> keys) {
		
		Iterator<SelectionKey> iterator = keys.iterator();	//Iteratore per il set di chiavi
		
		//Analizza tutte le chiavi
		while(iterator.hasNext()) {
			
			SelectionKey key = iterator.next();
			
			if(key.isValid()) {
				if(key.isAcceptable()) {
					try {
						acceptKey(channel,key);
					} catch (IOException e) {
						if(DEBUG) System.err.println("Error in accepting key "+key.toString());
						e.printStackTrace();
					}
				}
				else if(key.isReadable()) readKey(key);
				else if(key.isWritable()) writeKey(key);
				else if(DEBUG) System.err.println("Unsupported key "+key.toString());
			}
			else if(DEBUG) System.err.println("Invalid key");
			
			iterator.remove();
		}
		
	}
	
	/**
	 * metodo chiamato dal gestore delle chiavi per accettare una nuova richiesta TCP:
	 * la chiave associata al canale del server viene impostata come pronta per la lettura
	 * in modo da poter leggere il comando inviato dal client, inoltre viene creata una nuova
	 * sessione nella mappa this.sessions
	 * @param channel il canale da analizzare
	 * @param key la chiave da impostare in lettura
	 * @throws IOException se la connessione non può essere accettata o se la chiave non può
	 * essere impostata in lettura
	 */
	private void acceptKey(ServerSocketChannel channel, SelectionKey key) throws IOException {
		
		SocketChannel sockchan = channel.accept();	//Accetta la connessione
		
		sockchan.configureBlocking(false);
		
		sockchan.register(key.selector(), SelectionKey.OP_READ);
		
		this.sessions.put(sockchan, new StringBuilder());
		
		if(DEBUG) System.out.println("New client connection accepted");
		
	}

	/**
	 * metodo chiamato dal gestore delle chiavi per leggere la richiesta di un client:
	 * legge tutto il messaggio inviato dal client e lo salva nella sessione associata
	 * all'interno di this.sessions
	 * @param key la chiave da leggere
	 */
	private void readKey(SelectionKey key) {
		
		SocketChannel channel = (SocketChannel) key.channel();
		
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		
		try {
			
			int bytesread = channel.read(buffer);
			if(bytesread>0) {
				this.sessions.get(channel).replace(0, this.sessions.get(key.channel()).toString().length(), new String(buffer.array()));	//Salva il messaggio nella mappa delle sessioni
			}
			else if(bytesread<0) {	//Non è stato letto nessun messaggio
				this.sessions.remove(channel);	//Rimuove il client dalle sessioni
				channel.close();
				key.cancel();
			}
			
		} catch (IOException e) {
			if(DEBUG) System.err.println("Error in reading key "+key.toString());
			this.sessions.remove(channel);	//Rimuove il client dalle sessioni
			try {
				channel.close();
			} catch (IOException e1) {
				if(DEBUG) System.err.println("Error closing connection "+key.toString());
			}
			key.cancel();
		}
		
		analyzeRequest(key);	//Analizza ed esegue la richiesta
		
	}

	/**
	 * metodo chiamato dal gestore delle chiavi per scrivere una risposta ad un client:
	 * scrive tutto il messaggio all'interno di this.sessions sul canale associato
	 * alla chiave passata come parametro
	 * @param key la chiave da cui ricavare il canale su cui scrivere
	 */
	private void writeKey(SelectionKey key) {
		
		sendResponse(this.sessions.get(key.channel()).toString(), key);
		
		key.interestOps(SelectionKey.OP_READ);
	}
	
	/**
	 * metodo chiamato dopo aver interpretato un comando per salvare il messaggio di risposta,
	 * da mandare poi al client, all'interno della variabile this.sessions
	 * @param key la chiave associata al client a cui mandare poi la risposta
	 * @param msg il messaggio da salvare come risposta alla richiesta TCP
	 */
	private void replaceClientMsg(SelectionKey key, String msg) {
		
		this.sessions.get(key.channel()).replace(0, this.sessions.get(key.channel()).toString().length(), msg);
	}
	
	/**
	 * analizzatore delle richieste TCP
	 * metodo chiamato dal gestore delle chiavi per analizzare la richiesta di un client:
	 * ricerca nella mappa delle sessioni la chiave parametro e fa il parsing del messaggio
	 * ad essa associato, lo confronta con la lista di comandi disponibili e invoca
	 * il metodo corretto da eseguire
	 * @param key la chiave associata al client
	 */
	private void analyzeRequest(SelectionKey key) {
		
		if(key==null||key.channel()==null||this.sessions.isEmpty()) {	//Ignora richiesta
			return;
		}
		
		String clientmsg = this.sessions.get(key.channel()).toString();
		
		//Fa il parsing del messaggio ricevuto
		StringTokenizer tokenizer = new StringTokenizer(clientmsg);
		
		String command = tokenizer.nextToken();	//Salva il comando ricevuto
		
		boolean didlogout;	//Utilizzata per la terminazione della connessione TCP
		
		didlogout = false;
		
		switch(command.toLowerCase()) {
		
			case "login":
				
				if(tokenizer.countTokens()!=2) {	//Il comando login richiede 2 argomenti (username, password)
					
					replaceClientMsg(key, "Errore: sono richiesti username e password");
				}
				
				else {
					String username = tokenizer.nextToken().trim();
					String password = tokenizer.nextToken().trim();
					
					login(username, password, key);
				}
				
				break;
				
			case "logout":
				if(tokenizer.countTokens()!=1) {
					
					replaceClientMsg(key, "Errore: è richiesto l' username per il logout");
				}
				
				else {
					String username = tokenizer.nextToken().trim();
					
					didlogout = logout(username, key);
				}
				
				break;
			
			case "listprojects":
				
				String name = tokenizer.nextToken().trim();
				
				listProjects(name, key);
				
				break;
				
			case "createproject":
				
				if(tokenizer.countTokens()!=2) {
					replaceClientMsg(key, "Errore: è richiesto un nome per il progetto");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String username = tokenizer.nextToken().trim();
					
					createProject(projectname, username, key);
				}
				
				break;
				
			case "addmember":
				
				if(tokenizer.countTokens()!=3) {
					replaceClientMsg(key, "Errore: sono richiesti nome del progetto e nickname del nuovo membro");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String newmemberusername = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					addMember(projectname, newmemberusername, clientusername, key);
				}
				
				break;
				
			case "showmembers":
				
				if(tokenizer.countTokens()!=2) {
					replaceClientMsg(key, "Errore: è richiesto il nome del progetto");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					showMembers(projectname, clientusername, key);
				}
				
				break;
				
			case "showcards":
				
				if(tokenizer.countTokens()!=2) {
					replaceClientMsg(key, "Errore: è richiesto il nome del progetto");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					showCards(projectname, clientusername, key);
				}
				
				break;
				
			case "showcard":
				
				if(tokenizer.countTokens()!=3) {
					replaceClientMsg(key, "Errore: sono richiesti il nome del progetto e il nome della card");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String cardname = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					showCard(projectname, cardname, clientusername, key);
				}
				
				break;
				
			case "addcard":
				
				if(tokenizer.countTokens()!=4) {
					replaceClientMsg(key, "Errore: sono richiesti il nome del progetto, il nome della card ed una breve descrizione");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String cardname = tokenizer.nextToken().trim();
					
					String description = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					addCard(projectname, cardname, description, clientusername, key);
				}
				
				break;
				
			case "movecard":
				
				if(tokenizer.countTokens()!=5) {
					replaceClientMsg(key, "Errore: sono richiesti il nome del progetto, il nome della card, la lista di partenza e la lista destinazione");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String cardname = tokenizer.nextToken().trim();
					
					String fromlist = tokenizer.nextToken().trim();
					
					String tolist = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					moveCard(projectname, cardname, fromlist, tolist, clientusername, key);
				}
				
				break;
				
			case "getcardhistory":
				
				if(tokenizer.countTokens()!=3) {
					replaceClientMsg(key, "Errore: sono richiesti il nome del progetto e il nome della card");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String cardname = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					getCardHistory(projectname, cardname, clientusername, key);
				}
				
				break;
				
			case "readchat":
				
				if(tokenizer.countTokens()!=2) {
					replaceClientMsg(key, "Errore: è richiesto il nome del progetto");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String clientusername = tokenizer.nextToken().trim();
					
					readChat(projectname, clientusername, key);
				}
				
				break;
				
			case "cancelproject":
				
				if(tokenizer.countTokens()!=2) {
					replaceClientMsg(key, "Errore: è richiesto il nome del progetto");
				}
				
				else {
					String projectname = tokenizer.nextToken().trim();
					
					String username = tokenizer.nextToken().trim();
					
					cancelProject(projectname, username, key);
				}
				
				break;
				
			default:
				
				replaceClientMsg(key, "Errore: comando inesistente");
				break;
		}
		
		if(didlogout) {
			try {
				key.channel().close();	//TERMINA CONNESSIONE TCP
			} catch (IOException e) {
				if(DEBUG) System.err.println("Can't terminate TCP connection for key "+key.toString());
				key.interestOps(SelectionKey.OP_WRITE);
			}
			if(DEBUG) System.out.println("TCP connection closed for a client");
			this.sessions.remove(key.channel());
			key.cancel();
		}
		else
			key.interestOps(SelectionKey.OP_WRITE);
		
	}

	/**
	 * metodo chiamato per inviare la risposta ad un comando ricevuto da un client tramite TCP
	 * @param message il messaggio da inviare al client
	 * @param key la chiave associata al client
	 */
	private void sendResponse(String message, SelectionKey key) {

		ByteBuffer buffer = ByteBuffer.wrap(message.trim().getBytes());	//Salva il messaggio nel buffer
		
		SocketChannel sc = (SocketChannel) key.channel();
		
		while(buffer.hasRemaining()) {
			try {
				sc.write(buffer);	//Invia il messaggio al client
			} catch (IOException e) {
				if(DEBUG) System.out.println("Error sending TCP response to client with key "+key.toString());
				this.sessions.remove(key.channel());	//Rimuove il client dalle sessioni
				try {
					key.channel().close();
				} catch (IOException e1) {
					if(DEBUG) System.err.println("Error closing connection "+key.toString());
				}
				key.cancel();
				break;
			}
		}
		
	}

	/**
	 * metodo chiamato dal client via RMI per la registrazione
	 * il controllo sui parametri viene effettuato sia dal client che dal server
	 * synchronized in quanto modifica il file degli utenti
	 * modifica il file specificato nella variabile this.usersFile
	 */
	public synchronized String register(String nickUtente, String password)
			throws RemoteException, EmptyUserException, EmptyPasswordException, UserAlreadyRegisteredException {

		//Controlla che nome utente e password non siano vuoti
		if(nickUtente.isBlank()) throw new EmptyUserException();
		if(password.isBlank()) throw new EmptyPasswordException();
		
		if(DEBUG) System.out.println("Received register command");
		
		//Controlla se c'è già un altro utente registrato con lo stesso nome
		List<User> listautenti = Arrays.asList(this.registeredUsers);
		
		if(!listautenti.isEmpty()) {
			//Ci sono altri utenti registrati
			
			for(User u : listautenti) {
				if(u.getUsername().equals(nickUtente))
					return "Un altro utente è già registrato con quel nome utente";
			}
			
		}
		
		//Non c'è un altro utente con il nome desiderato, registra il nuovo utente
		User u = new User();
		u.setUsername(nickUtente);
		u.setPassword(password);
		
		List<User> nuovalista = new ArrayList<User>(listautenti.size()+1);

		nuovalista.addAll(listautenti);	//Rimette tutti gli utenti registrati in una nuova lista
		
		nuovalista.add(u);	//Aggiunge il nuovo utente alla nuova lista
		
		this.registeredUsers = nuovalista.toArray(this.registeredUsers);	//Salva la nuova lista nella variabile del server
		
		this.users.put(nickUtente, false);	//Salva l'utente nella struttura dati usata per callback
		
		//Salva la nuova lista sul file di registrazione
		File f = new File(this.usersFile);
		assert f.exists();	//Assicura che il file esiste
		
		//Scrive sul file in formato JSON la nuova lista
		ObjectMapper mapper = new ObjectMapper();
		
		ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
		
		try {
			writer.writeValue(f, this.registeredUsers);
		} catch (JsonGenerationException | JsonMappingException e) {
			if(DEBUG) System.err.println("Errore registrazione: fallita scrittura oggetto JSON su file"+this.usersFile);
			e.printStackTrace();
			return "Errore interno del server";
		} catch (IOException e) {
			if(DEBUG) System.err.println("Errore registrazione: fallita scrittura su file"+this.usersFile);
			e.printStackTrace();
			return "Errore interno del server";
		}
		
		//Aggiorna per tutti i client la lista degli utenti registrati
		updateRegisteredUsers();
		
		return "Registrazione effettuata";
		
	}

	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un login:
	 * ricerca tra gli utenti registrati l'username passato come argomento, se lo trova
	 * e la password corrisponde a quella di registrazione allora
	 * imposta l'utente come pubblicamente online aggiornando this.users e lo aggiunge alla lista locale
	 * this.onlineUsers utilizzata dal server
	 * altrimenti invia un messaggio di errore tramite TCP al client
	 * @param username l'username da ricercare tra gli utenti registrati
	 * @param password la password inserita dall'utente
	 * @param key la chiave associata al client da notificare
	 */
	private void login(String username, String password, SelectionKey key) {
		
		for(User u : getUsers()) {	//Controlla se esiste un utente con l'username e la password indicati
			
			if(u.getUsername().equals(username.trim())) {
				if(u.getPassword().equals(password.trim())) {
					//Username e password ricevuti corretti
					
					List<User> nuovalista = new ArrayList<User>(getOnlineUsers().size()+1);

					nuovalista.addAll(getOnlineUsers());	//Rimette tutti gli utenti online in una nuova lista
					
					nuovalista.add(u);	//Aggiunge il nuovo utente alla nuova lista
					
					this.onlineUsers = nuovalista.toArray(this.onlineUsers);	//Salva la nuova lista nella variabile del server
					
					this.users.put(username.trim(), true);	//Imposta l'utente come pubblicamente online
					
					replaceClientMsg(key, "ok");
					return;
				}
				else {
					//Password non corretta
					if(DEBUG) System.err.println("Wrong password");
					replaceClientMsg(key, "Password errata");
					return;
				}
			}
		}
		
		if(DEBUG) System.err.println("User not found");
		replaceClientMsg(key, "Utente non trovato");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un logout:
	 * ricerca tra gli utenti online l'username passato come argomento, se lo trova
	 * imposta l'utente come pubblicamente offline aggiornando this.users e lo rimuove dalla lista locale
	 * this.onlineUsers utilizzata dal server
	 * altrimenti invia un messaggio di errore tramite TCP al client
	 * @param username l'username da ricercare tra gli utenti online
	 * @param key la chiave associata al client da notificare
	 * @return true se l'utente ha effettuato correttamente il logout, false altrimenti
	 */
	private boolean logout(String username, SelectionKey key) {
		
		for(User u: getOnlineUsers()) {
			
			if(u.getUsername().equals(username.trim())) {	//Utente online trovato
				
				List<User> nuovalista = new ArrayList<User>(getOnlineUsers().size());

				nuovalista.addAll(getOnlineUsers());	//Rimette tutti gli utenti online in una nuova lista
				
				nuovalista.remove(u);	//Rimuove l'utente dalla nuova lista
				
				//Deve rimuovere l'elemento null dalla lista
				List<User> cleanlist = cleanList(nuovalista);
				
				User[] newarray = new User[cleanlist.size()];
				
				newarray = cleanlist.toArray(newarray);
				
				this.onlineUsers = null;
				
				this.onlineUsers = newarray.clone();
				
				this.users.put(username.trim(), false);	//Imposta l'utente come pubblicamente offline
				
				replaceClientMsg(key, "ok");
				return true;
			}
		}
		//Utente online non trovato
		replaceClientMsg(key, "Errore del server: impossibile eseguire logout per "+username);
		if(DEBUG) System.err.println("Can't execute logout for user "+username);
		
		return false;
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un listProjects:
	 * ricerca tra gli utenti online l'username passato come argomento, se lo trova
	 * ricerca tra tutti i progetti il suo username tra i membri, se lo trova
	 * aggiunge il nome del progetto alla risposta da inviare al client
	 * @param username l'username da ricercare tra gli utenti online e tra i membri dei progetti
	 * @param key la chiave associata al client da notificare
	 */
	private void listProjects(String username, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(username)) { //L'utente che ha richiesto la lista è online
				
				for(Project p : this.projects) {

					for(String nickname : p.getMembers()) {
						
						if(nickname.equals(username)) {	//L'utente è membro del progetto
							
							sb.append(p.getProjectName().trim());
							sb.append(System.getProperty("line.separator"));
							break;
						}
					}
				}
				
				if(sb.length()==0) {
					replaceClientMsg(key, "Errore: impossibile trovare progetti di cui fai parte");
					return;
				}
				else {
					replaceClientMsg(key, sb.toString());
					return;
				}
			}
		}
		//Utente non online
		replaceClientMsg(key, "Errore: per vedere la lista dei progetti di cui fai parte devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un createProject:
	 * ricerca tra gli utenti online l'username passato come argomento, se lo trova
	 * ricerca tra tutti i progetti un progetto con il nome passato come parametro,
	 * se lo trova restituisce un messaggio di errore altrimenti
	 * crea una nuova cartella con il nome desiderato all'interno di this.projectsFolder
	 * e all'interno un file project.json che memorizza lo stato del progetto
	 * @param projectname il nome del progetto da creare
	 * @param username l'username da ricercare tra gli utenti online
	 * @param key la chiave associata al client da notificare
	 */
	private void createProject(String projectname, String username, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length!=0) {	//Ci sono altri progetti
			for(Project p : getProjects()) {	//Controlla che non esiste già un altro progetto con il nome richiesto
				if(p.getProjectName().equals(projectname)) {
					replaceClientMsg(key, "Errore: esiste già un altro progetto con il nome scelto");
					return;
				}
			}
		}
		//Non esiste un progetto con il nome desiderato, lo crea
		
		for(User u : getOnlineUsers()) {
			if(u.getUsername().equals(username)) {	//L'utente che ha richiesto la creazione del progetto è online
				
				//Crea il progetto localmente e poi lo salva sul file project.json interno ad una nuova cartella con il nome desiderato
				Project p = new Project();
				
				ArrayList<String> members = new ArrayList<String>();
				
				p.setProjectName(projectname);
				p.setCreatedBy(username);
				members.add(username);
				p.setMembers(members);
				
				File projectsdir = new File(this.projectsFolder);
				
				assert(projectsdir.exists());	//Assicura che la directory dei progetti esiste
				
				File projectnewdir = new File(projectsdir+File.separator+projectname);
				
				if(!projectnewdir.mkdir()) {
					if(DEBUG) System.err.println("Error in creating folder "+projectnewdir);
					replaceClientMsg(key, "Errore del server: impossibile creare un progetto con il nome scelto");
					return;
				}
				else {	//Cartella del nuovo progetto creata correttamente
					//Crea il file project.json e scrive il nuovo progetto creato sul file
					File projectfile = new File(projectnewdir+File.separator+"project.json");
					
					try {
						projectfile.createNewFile();
					} catch (IOException e) {
						if(DEBUG) System.err.println("Error in creating file "+projectfile);
						projectnewdir.delete();	//Cancella la cartella creata
						replaceClientMsg(key, "Errore del server: impossibile creare un progetto con il nome scelto");
						return;
					}
					//File project.json creato, scrive l'oggetto progetto nel file
					
					ObjectMapper mapper = new ObjectMapper();
					
					ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
					
					try {
						writer.writeValue(projectfile, p);
					} catch (IOException e) {
						if(DEBUG) System.err.println("Error in writing on file "+projectfile);
						projectfile.delete();	//Cancella il file project.json
						projectnewdir.delete();	//Cancella la cartella creata
						replaceClientMsg(key, "Errore del server: impossibile creare un progetto con il nome scelto");
						return;
					}
					
					//File project.json scritto correttamente, salva il nuovo progetto localmente
					List<Project> newprojectlist = new ArrayList<Project>(getProjects().size()+1);
					
					newprojectlist.addAll(getProjects());
					
					newprojectlist.add(p);
					
					this.projects = newprojectlist.toArray(this.projects);
				}
				//Avvia il servizio chat per il progetto
				int n1,n2,n3,n4;
				if(this.firstfreeaddress==null) {	//Non ci sono ancora progetti
					n1 = 224;
					n2 = 0;
					n3 = 0;
					n4 = 2;
				}
				else {
					String[] parts = this.firstfreeaddress.split(" ");
					n1 = Integer.parseInt(parts[0]);
					n2 = Integer.parseInt(parts[1]);
					n3 = Integer.parseInt(parts[2]);
					n4 = Integer.parseInt(parts[3]);
				}
				int i = 0, j = 0, k = 0, l = 0;
				if(i<255) {
					p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
					i++;
				}
				else if(j<255) {
					j++;
					i=0;
					p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
				}
				else if(k<255) {
					k++;
					i=0;
					j=0;
					p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
				}
				else if(l<239) {
					l++;
					i=0;
					j=0;
					k=0;
					p.startChatService(this.chatPort,n1+l,n2+k,n3+j,n4+i);
				}
				
				this.firstfreeaddress = String.valueOf(n1+l)+" "+String.valueOf(n2+k)+" "+String.valueOf(n3+j)+" "+String.valueOf(n4+i+1);
				
				replaceClientMsg(key, "ok");
				return;
			}
		}
		//L'utente che ha richiesto la creazione del progetto non è online
		replaceClientMsg(key, "Errore: per creare un progetto devi aver effettuato il login");
		
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un addMember:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca l'username del nuovo membro tra i membri registrati su WORTH, se lo trova
	 * ricerca il progetto da modificare e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto la modifica, se lo trova
	 * aggiorna la lista dei membri del progetto aggiungendo l'username richiesto
	 * @param projectname il nome del progetto da modificare
	 * @param newmemberusername il nome del nuovo membro da aggiungere al progetto
	 * @param clientusername il nome dell'utente che ha richiesto la modifica del progetto
	 * @param key la chiave associata al client da notificare
	 */
	private void addMember(String projectname, String newmemberusername, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto l'aggiunta di un membro è online
				
				for(User k : this.registeredUsers) {
					if(k.getUsername().equals(newmemberusername)) {	//Il nuovo membro è un utente di WORTH
						
						for(Project p : this.projects) {
							if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
								
								for(String member : p.getMembers()) {
									if(member.equals(clientusername)) {	//L'utente che ha richiesto l'aggiunta di un membro fa parte del progetto
										
										ArrayList<String> newmembers = new ArrayList<String>(p.getMembers().size()+1);
										
										newmembers.addAll(p.getMembers());	//Inserisce tutti i vecchi membri del progetto in una nuova lista
										
										newmembers.add(newmemberusername);	//Aggiunge il nuovo membro al progetto
										
										p.setMembers(newmembers);	//Aggiorna la lista del progetto localmente
										
										//Aggiorna il file project.json
										File project = new File(this.projectsFolder+File.separator+projectname+File.separator+"project.json");
										
										if(!project.exists()) {	//File json non trovato
											replaceClientMsg(key, "Errore del server");
											if(DEBUG) System.out.print("Error in adding member for project "+projectname+": ");
											if(DEBUG) System.out.println("Can't locate file "+project);
											return;
										}
										
										ObjectMapper mapper = new ObjectMapper();
										
										ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
										
										try {
											writer.writeValue(project, p);
										} catch (IOException e) {
											if(DEBUG) System.err.println("Error in writing on file "+project);
											replaceClientMsg(key, "Errore del server");
											return;
										}
										
										replaceClientMsg(key, "ok");
										return;
									}
								}
								//L'utente non fa parte del progetto
								replaceClientMsg(key, "Errore: devi essere membro del progetto per poter aggiungere un nuovo partecipante");
								return;
							}
						}
						//Progetto non trovato
						replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
						return;
					}
				}
				//Il nuovo membro non è registrato a WORTH
				replaceClientMsg(key, "Errore: l'utente specificato non è registrato a WORTH");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per aggiungere un membro ad un progetto devi aver effettuato il login");
		
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un showMembers:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca tra i progetti quello ricevuto come parametro e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto il comando, e se lo trova
	 * notifica al client la lista dei membri del progetto
	 * @param projectname il nome del progetto
	 * @param clientusername il nickname dell'utente che ha richiesto il comando
	 * @param key la chiave associata al client da notificare
	 */
	private void showMembers(String projectname, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto la lista dei membri è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto la lista dei membri fa parte del progetto
								
								StringBuilder sb = new StringBuilder();
								
								List<String> members = p.getMembers();
								
								for(String user : members) {
									sb.append(user.trim());
									sb.append(System.getProperty("line.separator"));
								}
								
								replaceClientMsg(key, sb.toString());
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poterne vedere i partecipanti");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per visualizzare i membri di un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un showCards:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca tra i progetti quello ricevuto come parametro e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto il comando, e se lo trova
	 * notifica al client la lista delle card del progetto
	 * @param projectname il nome del progetto
	 * @param clientusername il nickname dell'utente che ha richiesto il comando
	 * @param key la chiave associata al client da notificare
	 */
	private void showCards(String projectname, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto la lista delle card è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto le card fa parte del progetto
								
								if(p.getAllCards().size()==0) {	//Non ci sono card nel progetto
									replaceClientMsg(key, "Non sono state trovate card nel progetto");
									return;
								}
								
								StringBuilder sb = new StringBuilder();
								
								for(Card c : p.getAllCards()) {
									sb.append(c.getName()+" /");
									sb.append(" Status: "+c.getCurrentList());
									sb.append(System.getProperty("line.separator"));
								}
								
								replaceClientMsg(key, sb.toString());
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poterne vedere le card");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per visualizzare le card di un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un showCard:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca tra i progetti quello ricevuto come parametro e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto il comando, e se lo trova
	 * ricerca nella lista delle card del progetto quella con il nome specificato e la notifica al client
	 * @param projectname il nome del progetto
	 * @param cardname il nome della card richiesta
	 * @param clientusername il nickname dell'utente che ha richiesto il comando
	 * @param key la chiave associata al client da notificare
	 */
	private void showCard(String projectname, String cardname, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto la card è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto la card fa parte del progetto
								
								if(p.getAllCards().size()==0) {	//Non ci sono card nel progetto
									replaceClientMsg(key, "Non sono state trovate card nel progetto");
									return;
								}
								
								StringBuilder sb = new StringBuilder();
								
								for(Card c : p.getAllCards()) {
									if(c.getName().equals(cardname)) {	//Card specificata trovata
										sb.append(c.getName()+" /");
										sb.append(" Status: "+c.getCurrentList());
										replaceClientMsg(key, sb.toString());
										return;
									}
								}
								//Card specificata non trovata
								replaceClientMsg(key, "Errore: card specificata non trovata per il progetto");
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poterne vedere le card");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per visualizzare una card di un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un addCard:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca tra i progetti quello ricevuto come parametro e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto il comando, e se lo trova
	 * ricerca nella lista delle card del progetto quella con il nome specificato e se NON la trova
	 * crea una nuova card localmente e un nuovo file "cardname".json all'interno della cartella del progetto
	 * in cui viene serializzata la nuova card creata
	 * viene quindi aggiornato il file project.json
	 * @param projectname il nome del progetto
	 * @param cardname il nome della card richiesta
	 * @param description la descrizione testuale breve della card
	 * @param clientusername il nickname dell'utente che ha richiesto il comando
	 * @param key la chiave associata al client da notificare
	 */
	private void addCard(String projectname, String cardname, String description, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto la creazione della card è online
				
				if(cardname.equalsIgnoreCase("project")) {	//Nome vietato, andrebbe in conflitto con il file project.json
					replaceClientMsg(key, "Errore: nome vietato, scegliere un altro nome per la card");
					return;
				}
				
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto la creazione della card fa parte del progetto
								
								for(Card c : p.getAllCards()) {
									if(c.getName().equals(cardname)) {	//Esiste già una card con il nome richiesto
										replaceClientMsg(key, "Esiste già una card con questo nome nel progetto");
										return;
									}
								}
								
								Card c = new Card();	//Salva la card localmente
								
								c.setProjectname(projectname);
								
								c.setName(cardname);
								
								c.setDescription(description);
								
								//Crea un nuovo file "cardname".json nella cartella del progetto, se non esiste un'altra card con lo stesso nome
								File cardfile = new File(this.projectsFolder+File.separator+projectname+File.separator+cardname+".json");
								
								if(cardfile.exists()) {	//La card esiste già
									replaceClientMsg(key, "Esiste già una card con questo nome nel progetto");
									return;
								}
								
								try {
									cardfile.createNewFile();
								} catch (IOException e1) {
									if(DEBUG) System.err.println("Error in creating file "+cardfile);
									replaceClientMsg(key, "Errore del server");
									return;
								}
								
								ObjectMapper mapper = new ObjectMapper();
								
								ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
								
								try {
									writer.writeValue(cardfile, c);
								} catch (IOException e) {
									if(DEBUG) System.err.println("Error in writing on file "+cardfile);
									cardfile.delete();	//Elimina il file creato
									replaceClientMsg(key, "Errore del server");
									return;
								}
								
								ArrayList<Card> newtodolist = new ArrayList<Card>(p.getTodoList().size()+1);
								
								newtodolist.addAll(p.getTodoList());	//Rimette tutte le card già esistenti nella nuova lista
								
								newtodolist.add(c);	//Inserisce la nuova card nella nuova lista
								
								p.setTodoList(newtodolist);	//Aggiorna la lista localmente
								
								//Aggiorna il file project.json
								File project = new File(this.projectsFolder+File.separator+projectname+File.separator+"project.json");
								
								if(!project.exists()) {	//File json non trovato
									replaceClientMsg(key, "Errore del server");
									if(DEBUG) System.out.print("Error in adding card for project "+projectname+": ");
									if(DEBUG) System.out.println("Can't locate file "+project);
									return;
								}
								
								ObjectMapper mapper2 = new ObjectMapper();
								
								ObjectWriter writer2 = mapper2.writer(new DefaultPrettyPrinter());
								
								try {
									writer2.writeValue(project, p);
								} catch (IOException e) {
									if(DEBUG) System.err.println("Error in writing on file "+project);
									replaceClientMsg(key, "Errore del server");
									return;
								}
								
								//Variabili locali aggiornate e file aggiornati
								replaceClientMsg(key, "ok");
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poter creare la card");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per creare una card di un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un moveCard:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca tra i progetti quello ricevuto come parametro e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto il comando, e se lo trova
	 * ricerca nella lista delle card del progetto quella con il nome specificato e se la trova
	 * verifica che le liste ricevute sono diverse e sposta la card dalla lista "fromlist" alla lista "tolist"
	 * viene quindi aggiornato il file project.json
	 * ed il file associato alla card
	 * @param projectname il nome del progetto
	 * @param cardname il nome della card richiesta
	 * @param fromlist il nome della lista da cui spostare la card: Liste valide = "todo","inprogress","toberevised","done"
	 * @param tolist la lista in cui spostare la card: Liste valide = "todo","inprogress","toberevised","done"
	 * @param clientusername il nickname dell'utente che ha richiesto il comando
	 * @param key la chiave associata al client da notificare
	 */
	private void moveCard(String projectname, String cardname, String fromlist, String tolist, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto lo spostamento della card è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto lo spostamento della card fa parte del progetto
								
								if(p.getAllCards().size()==0) {	//Non ci sono card nel progetto
									replaceClientMsg(key, "Non sono state trovate card nel progetto");
									return;
								}
								
								for(Card c : p.getAllCards()) {
									if(c.getName().equals(cardname)) {	//Card specificata trovata
										
										if(c.getCurrentList().equalsIgnoreCase(fromlist)) {	//La card si trova nella lista specificata
											
											if(fromlist.equalsIgnoreCase(tolist)) {	//La lista di partenza è uguale alla lista destinazione
												replaceClientMsg(key, "Errore: la nuova lista per la card non può essere la lista attuale");
												return;
											}
											
											String fl = fromlist.toUpperCase();
											
											if(!fl.equals("TODO")&&!fl.equals("INPROGRESS")&&!fl.equals("TOBEREVISED")&&!fl.equals("DONE")) {
												replaceClientMsg(key, "Errore: lista di partenza sconosciuta, liste valide: todo, inprogress, toberevised, done");
												return;
											}
											
											ArrayList<Card> toupdatedlist = null;
											
											ArrayList<Card> fromupdatedlist = null;
											
											switch (tolist.toUpperCase()) {
												
												case "TODO":
													//Lista destinazione = todo, non si può spostare una card in todo
													replaceClientMsg(key, "Errore: non puoi spostare una card in TODO");
													return;
													
												case "INPROGRESS":
													//Lista destinazione = inprogress, posso muovere card da toberevised o da todo
													if(!fl.equals("TOBEREVISED")&&!fl.equals("TODO")) {
														replaceClientMsg(key, "Errore: non puoi spostare una card da "+fl+" a INPROGRESS");
														return;
													}
													else {
														if(fl.equals("TOBEREVISED")) {
															//Lista provenienza = toberevised
															c.setCurrentList("INPROGRESS");
															c.getHistory().add("Card spostata dalla lista "+fl+" alla lista INPROGRESS");
															toupdatedlist = new ArrayList<Card>(p.getInprogressList().size()+1);
															toupdatedlist.addAll(p.getInprogressList());
															toupdatedlist.add(c);
															fromupdatedlist = new ArrayList<Card>(p.getToberevisedList().size());
															fromupdatedlist.addAll(p.getToberevisedList());
															fromupdatedlist.remove(c);
															p.setInprogressList(toupdatedlist);
															p.setToberevisedList(fromupdatedlist);
														}
														else {
															//Lista provenienza = todo
															c.setCurrentList("INPROGRESS");
															c.getHistory().add("Card spostata dalla lista "+fl+" alla lista INPROGRESS");
															toupdatedlist = new ArrayList<Card>(p.getInprogressList().size()+1);
															toupdatedlist.addAll(p.getInprogressList());
															toupdatedlist.add(c);
															fromupdatedlist = new ArrayList<Card>(p.getTodoList().size());
															fromupdatedlist.addAll(p.getTodoList());
															fromupdatedlist.remove(c);
															p.setInprogressList(toupdatedlist);
															p.setTodoList(fromupdatedlist);
														}
													}
													
													break;
													
												case "TOBEREVISED":
													//Lista destinazione = toberevised, posso muovere solo una card da inprogress
													if(!fl.equals("INPROGRESS")) {
														replaceClientMsg(key, "Errore: non puoi spostare una card da "+fl+" a TOBEREVISED");
														return;
													}
													else {
														c.setCurrentList("TOBEREVISED");
														c.getHistory().add("Card spostata dalla lista "+fl+" alla lista TOBEREVISED");
														toupdatedlist = new ArrayList<Card>(p.getToberevisedList().size()+1);
														toupdatedlist.addAll(p.getToberevisedList());
														toupdatedlist.add(c);
														fromupdatedlist = new ArrayList<Card>(p.getInprogressList().size());
														fromupdatedlist.addAll(p.getInprogressList());
														fromupdatedlist.remove(c);
														p.setToberevisedList(toupdatedlist);
														p.setInprogressList(fromupdatedlist);
													}
													
													break;
													
												case "DONE":
													//Lista destinazione = done, posso muovere una card da toberevised o da inprogress
													if(!fl.equals("TOBEREVISED")&&!fl.equals("INPROGRESS")) {
														replaceClientMsg(key, "Errore: non puoi spostare una card da "+fl+" a INPROGRESS");
														return;
													}
													else {
														if(fl.equals("TOBEREVISED")) {
															//Lista provenienza = toberevised
															c.setCurrentList("DONE");
															c.getHistory().add("Card spostata dalla lista "+fl+" alla lista DONE");
															toupdatedlist = new ArrayList<Card>(p.getDoneList().size()+1);
															toupdatedlist.addAll(p.getDoneList());
															toupdatedlist.add(c);
															fromupdatedlist = new ArrayList<Card>(p.getToberevisedList().size());
															fromupdatedlist.addAll(p.getToberevisedList());
															fromupdatedlist.remove(c);
															p.setDoneList(toupdatedlist);
															p.setToberevisedList(fromupdatedlist);
														}
														else {
															//Lista provenienza = inprogress
															c.setCurrentList("DONE");
															c.getHistory().add("Card spostata dalla lista "+fl+" alla lista DONE");
															toupdatedlist = new ArrayList<Card>(p.getDoneList().size()+1);
															toupdatedlist.addAll(p.getDoneList());
															toupdatedlist.add(c);
															fromupdatedlist = new ArrayList<Card>(p.getInprogressList().size());
															fromupdatedlist.addAll(p.getInprogressList());
															fromupdatedlist.remove(c);
															p.setDoneList(toupdatedlist);
															p.setInprogressList(fromupdatedlist);
														}
													}
													
													break;
													
												default:
													//Lista destinazione specificata non valida
													replaceClientMsg(key, "Errore: la nuova lista per la card non è una lista valida");
													return;
											}
											//Aggiorna i file project.json e "cardname".json
											
											File project = new File(this.projectsFolder+File.separator+projectname+File.separator+"project.json");
											
											if(!project.exists()) {	//File json non trovato
												replaceClientMsg(key, "Errore del server");
												if(DEBUG) System.out.print("Error in updating card for project "+projectname+": ");
												if(DEBUG) System.out.println("Can't locate file "+project);
												return;
											}
											
											ObjectMapper mapper = new ObjectMapper();
											
											ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
											
											try {
												writer.writeValue(project, p);
											} catch (IOException e) {
												if(DEBUG) System.err.println("Error in writing on file "+project);
												replaceClientMsg(key, "Errore del server");
												return;
											}
											
											File cardfile = new File(this.projectsFolder+File.separator+projectname+File.separator+cardname+".json");
											
											if(!cardfile.exists()) {	//File json non trovato
												replaceClientMsg(key, "Errore del server");
												if(DEBUG) System.out.print("Error in updating card for project "+projectname+": ");
												if(DEBUG) System.out.println("Can't locate file "+project);
												return;
											}
											
											ObjectMapper mapper2 = new ObjectMapper();
											
											ObjectWriter writer2 = mapper2.writer(new DefaultPrettyPrinter());
											
											try {
												writer2.writeValue(cardfile, c);
											} catch (IOException e) {
												if(DEBUG) System.err.println("Error in writing on file "+project);
												replaceClientMsg(key, "Errore del server");
												return;
											}
											
											replaceClientMsg(key, "ok");
											return;
										}
									}
								}
								//Card specificata non trovata
								replaceClientMsg(key, "Errore: card specificata non trovata per il progetto");
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poterne spostare le card");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per spostare una card di un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un getCardHistory:
	 * se l'utente che ha richiesto il comando risulta online allora
	 * ricerca tra i progetti quello ricevuto come parametro e se lo trova
	 * ricerca tra i membri del progetto l'utente che ha richiesto il comando, e se lo trova
	 * ricerca nella lista delle card del progetto quella con il nome specificato
	 * e notifica al client tutti gli eventi della card
	 * @param projectname il nome del progetto
	 * @param cardname il nome della card richiesta
	 * @param clientusername il nickname dell'utente che ha richiesto il comando
	 * @param key la chiave associata al client da notificare
	 */
	private void getCardHistory(String projectname, String cardname, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto la storia della card è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto la storia della card fa parte del progetto
								
								if(p.getAllCards().size()==0) {	//Non ci sono card nel progetto
									replaceClientMsg(key, "Non sono state trovate card nel progetto");
									return;
								}
								
								StringBuilder sb = new StringBuilder();
								
								for(Card c : p.getAllCards()) {
									if(c.getName().equals(cardname)) {	//Card specificata trovata
										
										ArrayList<String> history = c.getHistory();
										
										for(String event : history) {
											sb.append(event.trim());
											sb.append(System.getProperty("line.separator"));
										}
										
										replaceClientMsg(key, sb.toString());
										return;
									}
								}
								//Card specificata non trovata
								replaceClientMsg(key, "Errore: card specificata non trovata per il progetto");
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poterne vedere le card");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per visualizzare una storia di una card di un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un cancelProject:
	 * ricerca tra gli utenti online l'username passato come argomento, se lo trova
	 * ricerca tra tutti i progetti un progetto con il nome passato come parametro,
	 * se lo trova rimuove il progetto dalla lista locale this.projects ed
	 * elimina: la chat e la cartella associate al progetto
	 * @param projectname il nome del progetto da eliminare
	 * @param username l'username da ricercare tra gli utenti online
	 * @param key la chiave associata al client da notificare
	 */
	private void cancelProject(String projectname, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto la cancellazione del progetto è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto la cancellazione del progetto fa parte del progetto
								
								for(Card c : p.getAllCards()) {
									if(c.getCurrentList()!="DONE") {	//Esiste una card che non è nello stato DONE
										replaceClientMsg(key, "Errore: tutte le card del progetto "+projectname+
												" devono essere nello stato Done per poter cancellare un progetto");
										return;
									}
								}
								
								//Ferma il servizio chat associato al progetto
								p.stopChatService();
								
								//Cancella la variabile locale e la cartella del progetto
								List<Project> newprojectlist = new ArrayList<Project>(getProjects().size());
								
								newprojectlist.addAll(getProjects());
								
								newprojectlist.remove(p);
								
								//Deve rimuovere l'elemento null dalla lista
								List<Project> cleanlist = cleanList2(newprojectlist);
								
								Project[] newarray = new Project[cleanlist.size()];
								
								newarray = cleanlist.toArray(newarray);
								
								this.projects = null;
								
								this.projects = newarray.clone();
								
								File projectdir = new File(this.projectsFolder+File.separator+p.getProjectName());
								
								//Cancella i file interni alla cartella
								for(File f :projectdir.listFiles()) {
									if(!f.delete()) {
										if(DEBUG) System.out.println("Can't delete project file "+f);
										replaceClientMsg(key, "Errore del server: progetto cancellato temporaneamente fino al prossimo riavvio");
										return;
									}
								}
								
								if(projectdir.delete()) {
									//Cartella progetto cancellata
									replaceClientMsg(key, "ok");
									return;
								}
								else {
									if(DEBUG) System.out.println("Can't delete project folder "+projectdir);
									replaceClientMsg(key, "Errore del server: progetto cancellato temporaneamente fino al prossimo riavvio");
									return;
								}
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poterlo cancellare");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per cancellare un progetto devi aver effettuato il login");
	}
	
	/**
	 * metodo di utility chiamato per rimuovere gli elementi null da una lista di utenti
	 * @param lista degli utenti
	 * @return la lista senza oggetti null
	 */
	private List<User> cleanList(List<User> lista) {
		List<User> cleanlist = new ArrayList<User>();
		for(User u : lista) {
			if(u!=null) {
				cleanlist.add(u);
			}
		}
		
		return cleanlist;
	}
	
	/**
	 * metodo di utility chiamato per rimuovere gli elementi null da una lista di progetti
	 * @param lista dei progetti
	 * @return la lista senza oggetti null
	 */
	private List<Project> cleanList2(List<Project> lista) {
		List<Project> cleanlist = new ArrayList<Project>();
		for(Project p : lista) {
			if(p!=null) {
				cleanlist.add(p);
			}
		}
		
		return cleanlist;
	}
	
	/**
	 * metodo chiamato da un client via RMI che vuole mandare un messaggio su un progetto via multicast
	 * verifica che il client abbia i permessi di mandare il messaggio
	 * e se le condizioni sono soddisfatte ritorna l'indirizzo multicast associato al progetto
	 * su cui il client potrà mandare il messaggio
	 * @param projectname il nome del progetto su cui mandare il messaggio
	 * @param clientusername l'username da ricercare tra gli utenti online
	 * @return address l'indirizzo multicast su cui mandare il messaggio
	 */
	public String getMulticastAddress(String projectname, String clientusername) {
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			return null;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto di mandare un messaggio è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto di mandare un messaggio fa parte del progetto
								
								return p.retrieveAddress();
							}
						}
						//L'utente non fa parte del progetto
						if(DEBUG) System.out.println("utente non fa parte del progetto");
						return null;
					}
				}
				//Progetto non trovato
				if(DEBUG) System.out.println("progetto non trovato");
				return null;
			}
		}
		//L'utente che ha richiesto il comando non è online
		if(DEBUG) System.out.println("utente offline");
		return null;
	}
	
	/**
	 * metodo chiamato da un client via RMI dopo aver mandato un messaggio in multicast sulla chat di un progetto
	 * permette di salvare il messaggio nella struttura dati chat del progetto
	 * @param projectname il nome del progetto su cui è stato mandato il messaggio
	 */
	public void receiveChatMessage(String projectname) {
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			return;
		}
		
		for(Project p : this.projects) {
			if(p.getProjectName().equals(projectname.trim())) {	//Progetto trovato
				p.refreshChat();
				return;
			}
		}
	}
	
	/**
	 * metodo chiamato dall'analizzatore delle richieste TCP se il comando ricevuto è un readChat:
	 * ricerca tra gli utenti online l'username passato come argomento, se lo trova
	 * ricerca tra tutti i progetti un progetto con il nome passato come parametro,
	 * se lo trova ricerca il nome utente tra i membri del progetto e se lo trova
	 * restituisce i messaggi associati alla chat del progetto
	 * @param projectname il nome del progetto
	 * @param username l'username da ricercare tra gli utenti online
	 * @param key la chiave associata al client da notificare
	 */
	public void readChat(String projectname, String clientusername, SelectionKey key) {
		
		File projectfolder = new File(this.projectsFolder);
		
		if(projectfolder.list().length==0) {	//Non ci sono progetti
			replaceClientMsg(key, "Errore: impossibile trovare progetti");
			return;
		}
		
		for(User u : this.onlineUsers) {
			if(u.getUsername().equals(clientusername)) {	//L'utente che ha richiesto di mandare un messaggio è online
						
				for(Project p : this.projects) {
					if(p.getProjectName().equals(projectname)) {	//Progetto richiesto trovato
						
						for(String member : p.getMembers()) {
							if(member.equals(clientusername)) {	//L'utente che ha richiesto di mandare un messaggio fa parte del progetto
								
								StringBuilder sb = new StringBuilder();
								
								ArrayList<String> chat = p.retrieveChatMessages();
								
								sb.append("Inizio chat progetto "+p.getProjectName());
								sb.append(System.getProperty("line.separator"));
								
								for(String message : chat) {
									sb.append(message.trim());
									sb.append(System.getProperty("line.separator"));
								}

								sb.append("Fine chat progetto "+p.getProjectName());
								replaceClientMsg(key, sb.toString());
								return;
							}
						}
						//L'utente non fa parte del progetto
						replaceClientMsg(key, "Errore: devi essere membro del progetto per poter inviare un messaggio");
						return;
					}
				}
				//Progetto non trovato
				replaceClientMsg(key, "Errore: impossibile trovare il progetto specificato");
				return;
			}
		}
		//L'utente che ha richiesto il comando non è online
		replaceClientMsg(key, "Errore: per visualizzare una storia di una card di un progetto devi aver effettuato il login");
		return;
	}
	
	/**
	 * metodo chiamato per restituire la lista aggiornata degli
	 * utenti registrati
	 * @return la lista degli utenti registrati a worth
	 */
	private List<User> getUsers() {
		return Arrays.asList(this.registeredUsers);
	}
	
	/**
	 * metodo chiamato per restituire la mappa aggiornata degli
	 * utenti registrati
	 * @return la mappa username/status aggiornata degli utenti
	 */
	public Map<String, Boolean> getUsersData() {
		return this.users;
	}

	/**
	 * metodo chiamato per restituire la lista aggiornata degli
	 * utenti online
	 * @return la lista degli utenti attualmente online su worth
	 */
	private List<User> getOnlineUsers() {
		return Arrays.asList(this.onlineUsers);
	}
	
	/**
	 * metodo chiamato per restituire la lista aggiornata dei
	 * progetti
	 * @return la lista dei progetti aggiornata
	 */
	private List<Project> getProjects(){
		return Arrays.asList(this.projects);
	}
	
	/**
	 * metodo chiamato per aggiungere un client alla lista dei registrati al
	 * servizio callback
	 * @param client l'interfaccia associata al client da aggiungere
	 * alla lista
	 * @return true se l'interfaccia è stata aggiunta correttamente, false
	 * altrimenti
	 */
	public boolean addCallbackUser(CallbackUserInterface client) {
		if(this.callbackUsers.contains(client)) return false;
		boolean added = this.callbackUsers.add(client);
		if(added && DEBUG) System.out.println("Client added to callback service");
		return added;
	}
	
	/**
	 * metodo chiamato per rimuovere un client dalla lista dei registrati al
	 * servizio callback
	 * @param client l'interfaccia associata al client da rimuovere
	 * dalla lista
	 * @return true se l'interfaccia è stata rimossa correttamente, false
	 * altrimenti
	 */
	public boolean removeCallbackUser(CallbackUserInterface client) {
		boolean removed = this.callbackUsers.remove(client);
		if(removed && DEBUG) System.out.println("Client removed from callback service");
		return removed;
	}
	
	/**
	 * metodo chiamato per aggiornare, per tutti i client registrati al
	 * servizio callback, la lista degli utenti registrati a WORTH
	 */
	private void updateRegisteredUsers() {
		
		for(CallbackUserInterface ci : this.callbackUsers) {
			try {
				ci.updateAllUsers(this.users);
			} catch (RemoteException e) {
				if(DEBUG) 
					System.err.println("Can't update list of WORTH registered members for client "+ci);
			}
		}
		
	}

	public synchronized String needToSendMessage(String projectname, String username) throws RemoteException {
		return getMulticastAddress(projectname, username);
	}

	public synchronized void didSendMessage(String projectname) throws RemoteException {
		receiveChatMessage(projectname);
	}
	
}
