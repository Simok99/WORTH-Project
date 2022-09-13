package worth;

import java.rmi.RemoteException;
import java.util.Map;

import myInterfaces.CallbackServerInterface;
import myInterfaces.CallbackUserInterface;

/**
 * 
 * Implementazione della classe per le callback del client verso il server
 *
 */
public class CallbackServerImpl implements CallbackServerInterface{
	
	//Oggetto server utilizzato per le notify richieste dall'utente
	private Server server;
	
	/**
	 * metodo costruttore chiamato dal server per implementare
	 * il servizio RMI Callback
	 */
	public CallbackServerImpl(Server s) {
		super();
		this.server = s;
	}
	
	/**
	 * metodo chiamato dall'utente per registrarsi alla lista delle callback
	 * synchronized in quanto modifica la struttura dati condivisa
	 * this.clients
	 */
	public synchronized Map<String, Boolean> registerForCallback(CallbackUserInterface CallbackUserInterface) throws RemoteException {
		if(server.addCallbackUser(CallbackUserInterface)) {
			return server.getUsersData();
		}
		else return null;
	}

	/**
	 * metodo chiamato dall'utente per rimuoversi dalla lista dei registrati alle callback
	 * synchronized in quanto modifica la struttura dati condivisa
	 * this.clients
	 */
	public synchronized String unregisterForCallback(CallbackUserInterface CallbackUserInterface) throws RemoteException {
		if(server.removeCallbackUser(CallbackUserInterface)) {
			return "ok";
		}
		else return "Utente non trovato nel sistema callback";
	}

	/*
	 * metodo chiamato dall'utente per ricevere la struttura dati aggiornata
	 * degli utenti registrati
	 */
	public Map<String, Boolean> notifyMeAllUsers() throws RemoteException {
		return this.server.getUsersData();
	}

}
