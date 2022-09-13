package myExceptions;
/**
 * Eccezione che indica che il nome inserito dall'utente non � valido poich� vuoto
 */
public class EmptyUserException extends Exception {

	private static final long serialVersionUID = 1L;

	public EmptyUserException() {
		super("Il nome utente non pu� essere vuoto");
	}
}
