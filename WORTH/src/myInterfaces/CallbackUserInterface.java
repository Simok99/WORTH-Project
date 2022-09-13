package myInterfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * 
 * Interfaccia utilizzata dal server per effettuare callback all'utente
 *
 */
public interface CallbackUserInterface extends Remote {

	/**
	 * metodo utilizzato dal server per aggiornare la lista
	 * locale di un client degli utenti registrati
	 * @param la lista di utenti aggiornata
	 * @throws RemoteException
	 */
	public void updateAllUsers(Map<String, Boolean> users) throws RemoteException;
	
}
