package gz.server.net.boards.draughts;

import gz.server.net.boards.BoardLobby;

public class CheckersLobby extends BoardLobby {

	public CheckersLobby() {
		super();
	}

	@Override
	public CheckersContainer getContainer() {
		return (CheckersContainer) super.getContainer();
	}

	@Override
	protected boolean validateVariant(String variant) {
		return variant.equals("gzcheckers") || variant.equals("uscheckers") || variant.equals("brdraughts") || variant.equals("spdraughts") || variant.equals("rudraughts") || variant.equals("itdraughts");
	}

}
