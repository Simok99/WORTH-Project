package worth;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Project {
	
	//Variabili per un progetto
	private String projectName;	//Il nome del progetto
	private String createdBy;	//Il nome dell'utente che ha creato il progetto
	private String[] members;	//I nomi utente dei membri del progetto
	private ArrayList<Card> todoList;	//La lista contenente le card in stato "todo"
	private ArrayList<Card> inprogressList;	//La lista contenente le card in stato "inprogress"
	private ArrayList<Card> toberevisedList;	//La lista contenente le card in stato "toberevised"
	private ArrayList<Card> doneList;	//La lista contenente le card in stato "done"
	
	
	private int chatPort;	//La porta su cui aprire il gruppo multicast, decisa dal server
	private MulticastChat chat;
	
	/**
	 * costruttore per la classe project, viene invocato dal server
	 * quando c'è una richesta di creazione di un nuovo progetto
	 * o quando viene inizializzato dalla lettura dei file
	 */
	public Project() {
		
		initVariables();
		
	}

	/**
	 * metodo chiamato per inizializzare le variabili locali di un progetto
	 */
	private void initVariables() {

		this.members = new String[0];	//Nuovo array vuoto
		
		this.todoList = new ArrayList<Card>();
		
		this.inprogressList = new ArrayList<Card>();
		
		this.toberevisedList = new ArrayList<Card>();
		
		this.doneList = new ArrayList<Card>();
		
	}
	
	/**
	 * metodo chiamato alla creazione di un nuovo progetto, inizializza this.multicastSocket 
	 * e fa il join ad un gruppo su localhost alla porta this.chatPort
	 * rimane quindi in ascolto di messaggi sul gruppo e, quando ne viene ricevuto uno, viene
	 * salvato nella struttura dati locale this.chat
	 * @param serverThread il thread del server, utilizzato per la terminazione del servizio chat
	 * @param chatPort la porta su cui aprire il gruppo multicast per la chat
	 */
	public void startChatService(int chatPort, int n1, int n2, int n3, int n4) {
		
		MulticastChat chat = new MulticastChat(chatPort, n1, n2, n3, n4);
		
		this.chat = chat;
		
	}
	
	/**
	 * metodo chiamato alla cancellazione di un progetto, rimuove la reference all'oggetto
	 * multicast chat, che verrà eliminato dal garbage collector
	 */
	public void stopChatService() {
		
		//Rimuove la reference alla chat multicast, che verrà eliminata con il garbage collector
		this.chat = null;
	}
	
	/**
	 * metodo chiamato dal server per tentare la lettura di un nuovo messaggio
	 * sul gruppo multicast
	 * @throws IOException se il pacchetto non può essere ricevuto correttamente
	 * @throws SocketTimeoutException se il tempo per la ricezione di un nuovo messaggio è terminato
	 * 
	 */
	public void refreshChat() {
		
		this.chat.saveChatMessage();
		
	}
	
	/**
	 * metodo chiamato dal server per impostare il nome del progetto
	 * @param projectname il nome da assegnare al progetto
	 */
	public void setProjectName(String projectname) {
		this.projectName = projectname;
	}
	
	/**
	 * metodo chiamato dal server per impostare il creatore di un progetto
	 * @param username il nome utente del creatore del progetto
	 */
	public void setCreatedBy(String username) {
		this.createdBy = username;
	}
	
	/**
	 * metodo chiamato dal server per impostare i membri di un progetto
	 * @param members la lista dei membri del progetto
	 */
	public void setMembers(ArrayList<String> members) {
		this.members = members.toArray(this.members);
	}
	
	/**
	 * metodo chiamato dal server per impostare la lista todo del progetto
	 * @param members la lista todo del progetto
	 */
	public void setTodoList(ArrayList<Card> list) {
		this.todoList = list;
	}
	
	/**
	 * metodo chiamato dal server per impostare la lista inprogress del progetto
	 * @param members la lista inprogress del progetto
	 */
	public void setInprogressList(ArrayList<Card> list) {
		this.inprogressList = list;
	}
	
	/**
	 * metodo chiamato dal server per impostare la lista toberevised del progetto
	 * @param members la lista toberevised del progetto
	 */
	public void setToberevisedList(ArrayList<Card> list) {
		this.toberevisedList = list;
	}
	
	/**
	 * metodo chiamato dal server per impostare la lista done del progetto
	 * @param members la lista done del progetto
	 */
	public void setDoneList(ArrayList<Card> list) {
		this.doneList = list;
	}
	
	/**
	 * metodo chiamato per restituire il nome del progetto
	 * @return name il nome del progetto
	 */
	public String getProjectName() {
		return this.projectName;
	}
	
	/**
	 * metodo chiamato per restituire il nickname dell'utente che ha creato il progetto
	 * @return nickname l'username del creatore del progetto
	 */
	public String getCreatedBy() {
		return this.createdBy;
	}
	
	/**
	 * metodo chiamato per restituire la lista dei membri del progetto
	 * @return list la lista dei membri del progetto
	 */
	public List<String> getMembers() {
		return Arrays.asList(this.members);
	}

	/**
	 * metodo chiamato per restituire la lista di card con stato "todo"
	 * @return la lista di card con stato "todo"
	 */
	public ArrayList<Card> getTodoList() {
		return todoList;
	}

	/**
	 * metodo chiamato per restituire la lista di card con stato "inprogress"
	 * @return la lista di card con stato "inprogress"
	 */
	public ArrayList<Card> getInprogressList() {
		return inprogressList;
	}

	/**
	 * metodo chiamato per restituire la lista di card con stato "toberevised"
	 * @return la lista di card con stato "toberevised"
	 */
	public ArrayList<Card> getToberevisedList() {
		return toberevisedList;
	}

	/**
	 * metodo chiamato per restituire la lista di card con stato "done"
	 * @return la lista di card con stato "done"
	 */
	public ArrayList<Card> getDoneList() {
		return doneList;
	}
	
	@JsonIgnore
	/**
	 * metodo chiamato per restituire la lista di tutte le card associate al progetto
	 * @return la lista delle card del progetto
	 */
	public ArrayList<Card> getAllCards() {
		
		ArrayList<Card> cards = new ArrayList<Card>();
		
		cards.addAll(this.todoList);
		
		cards.addAll(this.inprogressList);
		
		cards.addAll(this.toberevisedList);
		
		cards.addAll(this.doneList);
		
		return cards;
	}
	
	/**
	 * metodo chiamato per restituire i messaggi nella chat associata al progetto
	 * @return la lista dei messaggi in chat
	 */
	public ArrayList<String> retrieveChatMessages() {
		return this.chat.chat;
	}
	
	/**
	 * metodo chiamato per restituire la porta della chat associata al progetto
	 * @return la porta per la chat multicast del progetto
	 */
	public int retrieveChatPort() {
		return this.chatPort;
	}
	
	/**
	 * metodo chiamato per restituire l'indirizzo della chat associata al progetto
	 * @return l'indirizzo della chat multicast del progetto
	 */
	public String retrieveAddress() {
		return this.chat.getAddress();
	}
}
