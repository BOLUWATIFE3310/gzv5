package gz.common.logic.billiards.carom;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class CaromGame extends BilliardsGame {

	protected int hitedBallCount;
	protected boolean hitCushion;

	public CaromGame(BilliardsGameController controller, int[] sqc) {
		super(controller, sqc);
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		currentState.setHandBallMode(0);

		if (!firstBallFault && hitCushion && hitedBallCount == 2) {
			changeTurn = false;
			currentPlayer.incrementScore();

			if (currentPlayer.getScore() == 8) {
				gameOver = true;
				winner = currentPlayer.getIndex();
			}
		}
	}

	@Override
	protected void checkHitBalls(BilliardsPlayer currentPlayer, int[] hitBallIndices) {
		super.checkHitBalls(currentPlayer, hitBallIndices);

		hitedBallCount = hitBallIndices != null ? hitBallIndices.length : 0;
	}

	@Override
	protected void checkPottedBalls(BilliardsPlayer currentPlayer, int[] pottedBallIndices, int[] pottedBallSlots) {
		hitCushion = pottedBallIndices.length > 0;
	}

	@Override
	public int[] initialBallSet() {
		return new int[] { 1, 2 };
	}

	@Override
	public int initialHandBallMode() {
		return 0;
	}

	@Override
	protected void initializeBallPositions() {
		initialPositions[0] = new Point2D(58500, b_ct);
		initialPositions[1] = new Point2D(21500, b_ct - 5000);
		initialPositions[2] = new Point2D(21500, b_ct);
	}

	@Override
	protected void initializeOffsets() {
		upk = 0;
		or = 985;
		ei = 9;
		bi = 7;
		bnm = 3;
		whsm = 5;
	}

	@Override
	public void updateGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pocketedBallIndices, int[] pocketedBallSlots, int[] hitBallIndices) {
		hitedBallCount = 0;
		hitCushion = false;

		super.updateGameState(cueBallIndex, ballIndices, ballPositions, pocketedBallIndices, pocketedBallSlots, hitBallIndices);
	}

}
