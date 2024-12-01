package gz.common.logic.boards.draughts;

import gz.common.logic.boards.BoardPlayer;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public final class DraughtsPlayer extends BoardPlayer {

	DraughtsPlayer(DraughtsGame game, int index, String name) {
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