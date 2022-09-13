package myExceptions;
/**
 * Eccezione che indica che la password inserita dall'utente non è valida poichè vuota
 */
public class EmptyPasswordException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public EmptyPasswordException() {
		super("La password non può essere vuota");
	}

}
