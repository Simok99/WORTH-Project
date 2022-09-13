package worth;

import java.rmi.RemoteException;

import myExceptions.EmptyPasswordException;
import myExceptions.EmptyUserException;
import myExceptions.UserAlreadyRegisteredException;
import myInterfaces.RMIUserInterface;

public class RMIUserImpl implements RMIUserInterface {

	private Server server;
	
	/**
	 * metodo costruttore utilizzato dal server per implementare il
	 * servizio RMI
	 */
	public RMIUserImpl(Server s) {
		super();
		this.server = s;
	}
	
	/**
	 * metodo utilizzato dall'utente per la registrazione tramite RMI
	 * @return ServerResponse il messaggio di risposta del server
	 * @throws RemoteException se il servizio RMI non risulta attivo
	 * @throws EmptyPasswordException se la password inviata e' vuota
	 * @throws UserAlreadyRegisteredException se un utente con lo stesso nome e' gia'
	 * stato registrato
	 */
	public String register(String nickUtente, String password)
			throws RemoteException, EmptyUserException, EmptyPasswordException, UserAlreadyRegisteredException {
		
		//Controlla che nome utente e password non siano vuoti
		if(nickUtente.isBlank()) throw new EmptyUserException();
		if(password.isBlank()) throw new EmptyPasswordException();
		
		String serverresponse = this.server.register(nickUtente, password);	//Chiede al server di registrarsi
		
		return serverresponse;
	}

	public String needToSendMessage(String projectname, String username) throws RemoteException {
		return this.server.needToSendMessage(projectname, username);
	}

	public void didSendMessage(String projectname) throws RemoteException {
		this.server.didSendMessage(projectname);
	}

}
