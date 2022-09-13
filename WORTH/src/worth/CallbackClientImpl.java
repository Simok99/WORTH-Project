package worth;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Map;

import myInterfaces.CallbackUserInterface;

/**
 * 
 * Implementazione della classe per le callback del server verso l'utente
 *
 */
public class CallbackClientImpl implements CallbackUserInterface {

	/**
	 * Struttura dati utilizzata dal client per visualizzare gli utenti registrati
	 * e il rispettivo status
	 */
	private Map<String, Boolean> users;
	
	/**
	 * metodo costruttore chiamato dall'utente quando
	 * si registra alle callback
	 */
	public CallbackClientImpl() {
		super();
	}
	
	/**
	 * metodo chiamato dal server per aggiornare la struttura dati degli utenti
	 * salvata localmente nel client
	 * @param allUsers la struttura dati di tutti gli utenti aggiornata
	 */
	public void updateAllUsers(Map<String, Boolean> allUsers) throws RemoteException {
		this.users = allUsers;
	}

}
