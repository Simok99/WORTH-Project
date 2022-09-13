package myInterfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * 
 * Interfaccia utilizzata dall'utente per registrarsi per le callback,
 * cancellare la registrazione e richiedere gli aggiornamenti
 * dello status degli utenti
 *
 */
public interface CallbackServerInterface extends Remote {

	/**
	 * metodo utilizzato da un utente per registrarsi al metodo
	 * callback del server
	 * @param CallbackUserInterface l'interfaccia che il server utilizzerà per notificare
	 * l'utente
	 * @return UsersData lo status degli utenti di WORTH
	 * @throws RemoteException
	 */
	public Map<String, Boolean> registerForCallback(CallbackUserInterface CallbackUserInterface)
			throws RemoteException;
	
	/**
	 * metodo utilizzato da un utente per rimuoversi dal metodo
	 * callback del server
	 * @param CallbackUserInterface l'interfaccia che il server deve rimuovere
	 * @throws RemoteException
	 */
	public String unregisterForCallback(CallbackUserInterface CallbackUserInterface)
			throws RemoteException;
	
	/**
	 * metodo utilizzato da un utente per richiedere tutti gli utenti
	 * registrati (aggiorna l'elenco locale del client)
	 * @return la struttura dati contenente tutti gli utenti registrati e lo status
	 * @throws RemoteException
	 */
	public Map<String, Boolean> notifyMeAllUsers() throws RemoteException;
	
}
