package worth;

import java.util.ArrayList;
import java.util.Date;

public class Card {

	private String projectname;	//Il nome del progetto in cui si trova la card
	private String currentList;	//Il nome della lista in cui si trova la card
	private String name;	//Il nome della card
	private String description;	//La descrizione testuale della card
	private ArrayList<String> history;	//La storia dei movimenti della card
	
	/**
	 * metodo costruttore chiamato dal server per la creazione di una nuova card
	 */
	public Card() {
		this.history = new ArrayList<String>();
		this.history.add(new Date(System.currentTimeMillis())+"  Card creata, inserita automaticamente in lista TODO");	//Nuova card creata
		this.setCurrentList("TODO");
	}
	
	/**
	 * metodo chiamato dal server per creare una nuova card
	 * @param projectname il nome del progetto in cui si trova la card
	 */
	public void setProjectname(String projectname) {
		this.projectname = projectname;
	}
	
	/**
	 * metodo chiamato dal server per modificare il nome della lista in cui si trova la card
	 * @param currentList il nuovo nome della lista
	 */
	public void setCurrentList(String currentList) {
		this.currentList = currentList;
	}
	
	/**
	 * metodo chiamato dal server per creare una nuova card
	 * @param name il nome da attribuire alla card
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * metodo chiamato dal server per modificare la descrizione della card
	 * @param description la descrizione da attribuire alla card
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * metodo chiamato dal server per modificare la storia della card
	 * @param hostiry la storia da attribuire alla card
	 */
	public void setHistory(ArrayList<String> history) {
		this.history = history;
	}
	
	/**
	 * metodo chiamato per restituire il nome del progetto in cui si trova la card
	 * @return projectname il nome del progetto in cui si trova la card
	 */
	public String getProjectname() {
		return this.projectname;
	}
	
	/**
	 * metodo chiamato per restituire il nome della lista in cui si trova attualmente la card
	 * @return il nome della lista in cui si trova la card
	 */
	public String getCurrentList() {
		return this.currentList;
	}
	
	/**
	 * metodo chiamato per restituire il nome della card
	 * @return il nome della card
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * metodo chiamato per restituire la descrizione della card
	 * @return la descrizione della card
	 */
	public String getDescription() {
		return this.description;
	}
	
	/**
	 * metodo chiamato per restituire la storia della card
	 * @return la storia della card
	 */
	public ArrayList<String> getHistory() {
		return this.history;
	}
	
}
