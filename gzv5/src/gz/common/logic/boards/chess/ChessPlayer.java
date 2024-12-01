package gz.common.logic.boards.chess;

import gz.common.logic.boards.BoardPlayer;

public class ChessPlayer extends BoardPlayer {

	protected ChessPlayer(ChessGame game, int index, String name) {
		super(game, index, name);
	}

	@Override
	protected void reportDraw() {
		super.reportDraw();
	}

	@Override
	protected void reportLoser() {
		super.reportLoser();
	}

	@Override
	protected void reportWinner() {
		super.reportWinner();
	}

}
