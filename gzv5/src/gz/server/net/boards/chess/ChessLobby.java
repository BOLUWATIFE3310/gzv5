package gz.server.net.boards.chess;

import gz.server.net.boards.BoardLobby;

public class ChessLobby extends BoardLobby {

	public ChessLobby() {
		super();
	}

	@Override
	public ChessContainer getContainer() {
		return (ChessContainer) super.getContainer();
	}

	@Override
	protected boolean validateVariant(String variant) {
		return variant.equals("gchess");
	}

}
