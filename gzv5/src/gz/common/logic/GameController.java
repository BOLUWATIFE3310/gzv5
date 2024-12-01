package gz.common.logic;

import common.process.ProcessQueue;
import gz.common.logic.Game.StopReason;

public interface GameController {

	ProcessQueue getQueue();

	void onChangeTurn(int turn);

	void onNextRound(int round);

	void onRotateLeft();

	void onRotateRight();

	void onStart();

	void onStarted();

	void onStop(StopReason reason);

	void onSwap(int index1, int index2);

}
