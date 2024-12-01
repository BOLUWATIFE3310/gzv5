package gz.common.logic.boards;

import gz.common.logic.GameController;

public interface BoardGameController extends GameController {

	void onPause(int time);

	void onPauseTimeOut();

	void onResume();

	void onUndoLastMove();

}
