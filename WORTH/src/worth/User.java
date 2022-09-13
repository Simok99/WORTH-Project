package worth;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

import myExceptions.EmptyPasswordException;
import myExceptions.EmptyUserException;
import myExceptions.UserAlreadyRegisteredException;
import myInterfaces.CallbackServerInterface;
import myInterfaces.CallbackUserInterface;
import myInterfaces.RMIUserInterface;

public class User implements Runnable {

	//Parametri e variabili dell'utente
	private String username;
	private String password;
	private boolean isLoggedIn;	//Rappresenta lo stato dell'utente: true = l'utente ha effettuato il login, false altrimenti
	
	//Parametri e variabili per la connessione TCP al server
	private int serverPort = 7777;
	private SocketChannel clientChannel;
	
	//Parametri e variabili per RMI e RMI callback
	private Registry RMIRegistry = null;
	private int RMIServicePort = 1234;
	private RMIUserInterface RMIUserInterface;	//Utilizzata per la registrazione
	private CallbackServerInterface CallbackServerInterface;	//Utilizzata per il servizio callback
	private CallbackUserInterface CallbackUserInterface;	//Utilizzata per la rimozione della registrazione alle callback
	private String RMIServiceName = "RMIService";
	private String RMICallbackServiceName = "RMICallbackService";
	private Map<String, Boolean> users;	//Contiene gli utenti ed il loro status associato su worth
	
	/**
	 * Costruttore per la classe utente, invocato dal main
	 * inizializza le variabili locali
	 */
	public User() {
		this.isLoggedIn = false;
	}
	
	/**
	 * metodo chiamato dal main o dal server per avviare un thread utente,
	 * gestisce tutte le operazioni dell'utente e termina quando
	 * si effettua il logout
	 */
	public void run() {
		
		boolean hasDone = false;
		
		while(!hasDone) {
			
			//System.out.flush(); //Forza la visualizzazione di messaggi prima di restituire l'input all'utente
			
			//System.err.flush();	//Forza la visualizzazione di errori prima di restituire l'input all'utente
			
			System.out.println();
			
			System.out.print(">");
			
			Scanner sc = new Scanner(System.in);
			
			String input = sc.nextLine();
			
			StringTokenizer tokenizer = new StringTokenizer(input);	//Per il parsing dell'input
			
			String command = tokenizer.nextToken();
			
			String serverresponse;
			
			switch(command) {
				case "register":
					
					if(this.isLoggedIn) {
						System.err.println("Sei attualmente loggato come "+this.username);
						break;
					}
					
					if(tokenizer.countTokens()!=2) {	//Non ci sono 2 argomenti (username, pass)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(this.RMIRegistry==null) {	//Non è stato definito il registro RMI
						
						Registry r = null;
						try {
							r = LocateRegistry.getRegistry(this.RMIServicePort);
						} catch (RemoteException e) {
							System.err.println("Errore: impossibile inizializzare servizio RMI");
							e.printStackTrace();
						}
						
						this.RMIRegistry = r;
					}
					
					//Registro RMI trovato
					
					searchRMIService();	//Ricerca il servizio RMI
					
					String username = tokenizer.nextToken();
					
					String password = tokenizer.nextToken();
					
					try {
					
						serverresponse = this.RMIUserInterface.register(username, password);
					
					} catch (RemoteException | EmptyUserException | EmptyPasswordException
						| UserAlreadyRegisteredException e) {
						System.out.println("<"+e);
						break;
					}
					
					System.out.println("<"+serverresponse.trim());	//Stampa la risposta del server
					
					break;
				
				case "login":
					
					if(this.isLoggedIn) {	//Utente già loggato in worth
						System.err.println("Sei attualmente loggato come "+this.username);
						break;
					}
					
					if(tokenizer.countTokens()!=2) {	//Non ci sono 2 argomenti (username, pass)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!initTCPConnection()) {
						System.err.println("Errore nella connessione al server");
						break;
					}
					
					if(!sendCommand(input)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					if(serverresponse.trim().equals("ok")) {
						//Login effettuato con successo
						this.isLoggedIn = true;
						
						this.username = tokenizer.nextToken().trim();
						
						this.password = tokenizer.nextToken().trim();
						
						if(this.RMIRegistry==null) {	//Non è stato definito il registro RMI
							Registry r = null;
							try {
								r = LocateRegistry.getRegistry(this.RMIServicePort);
							} catch (RemoteException e) {
								System.err.println("Errore: impossibile inizializzare servizio RMI");
								System.out.println("Risulterai loggato, ma non potrai vedere lo status degli altri utenti");
								e.printStackTrace();
							}
							
							this.RMIRegistry = r;
							
							if(this.RMIRegistry!=null) {
								//Registro RMI trovato
								
								searchRMICallbackService();	//Ricerca il servizio RMI per le callback
								
								registerToCallbackService();	//Registra il client al servizio RMI callback
							
							}
							break;
						}
						else {
							//Registro RMI trovato
							
							searchRMICallbackService();	//Ricerca il servizio RMI per le callback
							
							registerToCallbackService();	//Registra il client al servizio RMI callback
						
						}
						
					}
					
					break;
				
				case "logout":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=1) {	//Argomenti richiesti (username)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					if(serverresponse.trim().equals("")) {	//Connessione TCP Terminata
						
						System.out.println("<Logout effettuato");
						
						this.isLoggedIn = false;
						
						unregisterFromCallbackService();
						
						sc.close();
						
						hasDone = true;
						
						System.out.println("Logout effettuato, sessione terminata");
					}
					else
						System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "listUsers":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=0) {	//Argomenti richiesti nessuno
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					try {
						this.users = this.CallbackServerInterface.notifyMeAllUsers();	//Richiede al server la lista aggiornata degli utenti
					} catch (RemoteException e) {
						System.out.println("<"+e);	//Errore nella comunicazione con il server
						e.printStackTrace();
						break;
					}
					
					//Lista utenti aggiornata, visualizza tutti gli utenti e il loro status
					for(String user : this.users.keySet()) {
						System.out.println();
						System.out.print(user+":     ");
						
						if(this.users.get(user)) {
							//Utente online
							System.out.print("online");
						}
						else System.out.print("offline"); //Utente offline
					}
					
					System.out.println();
					
					break;
					
				case "listOnlineusers":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=0) {	//Argomenti richiesti nessuno
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					try {
						this.users = this.CallbackServerInterface.notifyMeAllUsers();	//Richiede al server la lista aggiornata degli utenti
					} catch (RemoteException e) {
						System.out.println("<"+e);	//Errore nella comunicazione con il server
						e.printStackTrace();
						break;
					}
					
					//Lista utenti aggiornata, visualizza tutti gli utenti online
					for(String user : this.users.keySet()) {
						System.out.println();
						
						if(this.users.get(user)) {
							//Utente online
							System.out.println(user);
						}
						
					}
					
					System.out.println();
					
					break;
					
				case "listProjects":

					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "createProject":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=1) {	//Argomenti richiesti (nome progetto)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "addMember":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=2) {	//Argomenti richiesti (nome progetto, nickname nuovo membro)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "showMembers":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=1) {	//Argomenti richiesti (nome progetto)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "showCards":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=1) {	//Argomenti richiesti (nome progetto)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "showCard":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=2) {	//Argomenti richiesti (nome progetto, nome card)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "addCard":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=3) {	//Argomenti richiesti (nome progetto, nome card, descrizione card)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "moveCard":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=4) {	//Argomenti richiesti (nome progetto, nome card, lista partenza, lista destinazione)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "getCardHistory":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=2) {	//Argomenti richiesti (nome progetto, nome card)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "cancelProject":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=1) {	//Argomenti richiesti (nome progetto)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "sendChatMsg":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()<2) {	//Argomenti richiesti (nome progetto, messaggio)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					String projectname = tokenizer.nextToken().trim();
					try {
						String addressreceived;
						searchRMIService();
						addressreceived=this.RMIUserInterface.needToSendMessage(projectname, this.username);
						if(addressreceived==null) {
							System.err.println("Errore: impossibile inviare un messaggio sul progetto richiesto");
							break;
						}
						else {
							//Fa il parsing dell'indirizzo
							String[] parts = addressreceived.split(":");
							String add = parts[0];
							String port = parts[1];
							
							//Invia il messaggio tramite multicast
							try {
								DatagramSocket socket = new DatagramSocket(1234);
								
								InetAddress address;
								
								address = InetAddress.getByName(add);
								
								StringBuilder sb = new StringBuilder();
								
								sb.append(this.username+": ");
								while(tokenizer.hasMoreTokens()) {	//Costruisce il messaggio da inviare
									sb.append(tokenizer.nextToken());
									sb.append(" ");
								}
								
								byte[] data = sb.toString().getBytes();
								
								DatagramPacket packet = new DatagramPacket(data, data.length, address, Integer.parseInt(port));
								
								socket.send(packet);
								
								System.out.println("<Messaggio inviato");
								
								socket.close();
								
							} catch (NumberFormatException | IOException e1) {
								System.err.println("Errore: impossibile inviare un messaggio sul progetto richiesto");
								break;
							}
						}
					} catch (RemoteException e) {
						e.printStackTrace();
						break;
					}
					
					//Messaggio inviato, informa il server di effettuare la lettura
					try {
						this.RMIUserInterface.didSendMessage(projectname);	//Informa il server di aver inviato un messaggio
					} catch (RemoteException e) {
						System.out.println("<"+e);	//Errore nella comunicazione con il server
						e.printStackTrace();
						break;
					}
					
					break;
					
				case "readChat":
					
					if(!this.isLoggedIn) {	//Utente non loggato in worth
						System.err.println("Non sei loggato su worth");
						break;
					}
					
					if(tokenizer.countTokens()!=1) {	//Argomenti richiesti (nome progetto)
						System.err.println("Errore argomenti");
						printHelp();
						break;
					}
					
					if(!sendCommand(input+" "+this.username)) {
						System.err.println("Errore nell' invio del comando al server");
						break;
					}
					
					try {
						serverresponse = receiveResponse();
					} catch (IOException e) {
						System.err.println("Errore nella ricezione della risposta del server");
						e.printStackTrace();
						break;
					}
					
					System.out.println("<"+serverresponse.trim());
					
					break;
					
				case "help":
					
					printHelp();
					break;
				
				default:
					
					printHelp();
					break;
			}
		}
		System.exit(0);
	}

	/**
	 * metodo chiamato per inizializzare la connessione TCP con il server alla porta this.serverPort
	 * @return true se la connessione è stata stabilita, false altrimenti
	 */
	private boolean initTCPConnection() {
		try {
			SocketChannel sc = SocketChannel.open();
			
			sc.connect(new InetSocketAddress(this.serverPort));
			
			this.clientChannel = sc;
			
			return true;
		} catch (IOException e) {
			System.err.println("Impossibile avviare connessione TCP con il server");
		}
		return false;
	}

	/**
	 * metodo chiamato per inviare un messaggio tramite TCP al server
	 * @param input il messaggio da inviare
	 * @return true se il messaggio è stato correttamente inviato al server,
	 * false altrimenti
	 */
	private boolean sendCommand(String input) {
		
		ByteBuffer buffer = ByteBuffer.wrap(input.getBytes());	//Buffer con il messaggio da inviare
		
		while(buffer.hasRemaining()) {
			try {
				this.clientChannel.write(buffer);	//Scrive il messaggio nel canale
			} catch (IOException e) {
				System.err.println("Impossibile inviare comando al server");
				e.printStackTrace();
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * metodo chiamato per leggere un messaggio inviato dal server al client
	 * tramite TCP
	 * @return il messaggio ricevuto tramite TCP
	 * @throws IOException se il messaggio non è stato ricevuto correttamente
	 */
	private String receiveResponse() throws IOException {

		ByteBuffer buffer = ByteBuffer.allocate(4096);
		
		StringBuilder sb = new StringBuilder();	//Ricostruisce il messaggio ricevuto
		
		int bytesread = this.clientChannel.read(buffer);
		
		if(bytesread>0) {
			sb.append(new String(buffer.array()));	//Salva il messaggio nello StringBuilder
		}
		else if(bytesread<0) {
			sb.append("");	//Messaggio vuoto
		}
		
		String msgreceived = sb.toString();
		
		return msgreceived;
	}
	
	/*
	 * metodo chiamato per restituire l'username associato a questo utente
	 */
	public String getUsername() {
		return this.username;
	}
	
	/*
	 * metodo chiamato per restituire la password associata a questo utente
	 */
	public String getPassword() {
		return this.password;
	}
	
	/*
	 * metodo chiamato dal server per impostare il nome utente di registrazione
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	
	/*
	 * metodo chiamato dal server per impostare la password di registrazione
	 */
	public void setPassword(String password) {
		this.password = password;
	}
	
	/**
	 * metodo chiamato per cercare il servizio RMI del server utilizzando
	 * nome = this.RMIServiceName e porta = this.RMIServicePort
	 */
	private void searchRMIService() {
		
		try {
			
			RMIUserInterface userinterface = 
					(RMIUserInterface) this.RMIRegistry.lookup(RMIServiceName);
			
			this.RMIUserInterface = userinterface;
			
		} catch (RemoteException e) {
			System.err.println("Errore: servizio RMI non trovato");
			e.printStackTrace();
		} catch (NotBoundException e) {
			System.err.println("Errore: servizio RMI non trovato");
			e.printStackTrace();
		}
	}
	
	/**
	 * metodo chiamato per cercare il servizio Callback RMI del server utilizzando
	 * nome = this.RMICallbackServiceName
	 */
	private void searchRMICallbackService() {
		
		try {
			
			CallbackServerInterface callbackinterface = 
					(CallbackServerInterface) this.RMIRegistry.lookup(this.RMICallbackServiceName);
			
			this.CallbackServerInterface  = callbackinterface;
			
		} catch (RemoteException e) {
			System.err.println("Errore: servizio RMI non trovato");
			e.printStackTrace();
		} catch (NotBoundException e) {
			System.err.println("Errore: servizio RMI non trovato");
			e.printStackTrace();
		}
	}
	
	/**
	 * metodo chiamato per registrare il client al servizio RMI Callback
	 */
	private void registerToCallbackService() {
		
		CallbackClientImpl CallbackClientImpl = new CallbackClientImpl();
		
		try {
			
			CallbackUserInterface userinterface =
					(CallbackUserInterface) UnicastRemoteObject.exportObject(CallbackClientImpl, 0);
			
			this.CallbackUserInterface = userinterface;
			
			this.users = this.CallbackServerInterface.registerForCallback(userinterface);	//Registra il client al servizio callback e aggiorna gli utenti
			
			if(this.users==null) {
				System.err.println("<Errore nella connessione al servizio RMI Callback");
			}
			
		} catch (RemoteException e) {
			System.err.println("Errore nella registrazione al servizio RMI Callback");
			e.printStackTrace();
		}
	}
	
	/**
	 * metodo chiamato per rimuovere la registrazione del client dal servizio RMI Callback
	 */
	private void unregisterFromCallbackService() {
		
		try {
			
			String serverresponse;
			
			serverresponse = this.CallbackServerInterface.unregisterForCallback(this.CallbackUserInterface);
			
			if(!serverresponse.contentEquals("ok")) {
				System.err.println("<Errore server: "+serverresponse.trim());
			}
			
		} catch (RemoteException e) {
			System.err.println("Errore nella rimozione della registrazione al servizio RMI Callback");
			e.printStackTrace();
		}
	}
	
	/**
	 * metodo chiamato per stampare la lista dei comandi
	 */
	private void printHelp() {
		System.out.println();
		System.out.println("---ELENCO DEI COMANDI---------------------------------------------------------------------------------");
		System.out.println("register username password		per registrarsi a WORTH");
		System.out.println("login username password 	per effettuare il login a WORTH");
		System.out.println("logout username		per effettuare il logout da WORTH");
		System.out.println("listUsers		per visualizzare gli utenti registrati a WORTH");
		System.out.println("listOnlineusers		per visualizzare gli utenti online su WORTH");
		System.out.println("listProjects		per visualizzare i tuoi progetti");
		System.out.println("createProject nomeprogetto		per creare un nuovo progetto");
		System.out.println("addMember nomeprogetto usernamenuovomembro		per aggiungere un utente registrato a WORTH ad un progetto di cui fai parte");
		System.out.println("showMembers nomeprogetto		per visualizzare i membri di un progetto di cui fai parte");
		System.out.println("showCards nomeprogetto		per visualizzare le card di un progetto di cui fai parte");
		System.out.println("showCard nomeprogetto nomecard		per visualizzare una specifica card di un progetto di cui fai parte");
		System.out.println("addCard nomeprogetto nomecard descrizione*		per aggiungere una card ad un progetto di cui fai parte");
		System.out.println("				*NB la descrizione deve essere considerata come una sola parola, se necessario utilizzare _");
		System.out.println("moveCard nomeprogetto nomecard listapartenza* listadestinazione*		per spostare una card da una lista ad un'altra");
		System.out.println("				*NB liste valide: todo, inprogress, toberevised, done (Case Insensitive)");
		System.out.println("getCardHistory nomeprogetto nomecard		per visualizzare la storia della card, ossia tutti i suoi spostamenti");
		System.out.println("readChat nomeprogetto		per leggere i messaggi in chat di un progetto di cui fai parte");
		System.out.println("sendChatMsg nomeprogetto messaggio		per inviare un messaggio in chat in un progetto di cui fai parte");
		System.out.println("cancelProject nomeprogetto		per cancellare un progetto di cui fai parte*");
		System.out.println("				*NB per poter cancellare un progetto, tutte le card devono essere nello stato DONE");
		System.out.println("-------------------------------------------------------------------------------------------------------");
		System.out.println("!!!Tutti i comandi sono Case Sensitive!!!");
	}
}
