package myInterfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

import myExceptions.EmptyPasswordException;
import myExceptions.EmptyUserException;
import myExceptions.UserAlreadyRegisteredException;

/**
 * 
 * Interfaccia utilizzata dall'utente per effettuare la registrazione
 * utilizzando RMI
 *
 */
public interface RMIUserInterface extends Remote {

	/**
	 * metodo utilizzato da un utente per effettuare la registrazione
	 * @param nickUtente il nome utente con cui l'utente si registra
	 * @param password la password associata al nome utente
	 * @return ServerResponse il messaggio di risposta del server
	 * @throws RemoteException se il servizio RMI del server non e' stato trovato
	 * @throws EmptyUserException se il nome utente risulta vuoto
	 * @throws EmptyPasswordException se la password risulta vuota
	 * @throws UserAlreadyRegisteredException se un altro utente e' gia' registrato con
	 * lo stesso nome utente
	 */
	public String register(String nickUtente, String password)
			throws RemoteException, EmptyUserException, EmptyPasswordException, UserAlreadyRegisteredException;
	
	
	/**
	 * metodo utilizzato da un utente per notificare il server di voler inviare un messaggio
	 * in chat
	 * @param projectname il nome del progetto in cui si vuole inviare il messaggio
	 * @param username il nome dell'utente che ha richiesto di inviare il messaggio
	 * @throws RemoteException
	 * @return String l'indirizzo associato al progetto su cui poter inviare il messaggio
	 */
	public String needToSendMessage(String projectname, String username) throws RemoteException;
	
	/**
	 * metodo utilizzato da un utente per notificare il server di aver inviato un messaggio
	 * in chat
	 * @param projectname il nome del progetto in cui è stato inviato il messaggio
	 * @throws RemoteException
	 */
	public void didSendMessage(String projectname) throws RemoteException;
}
