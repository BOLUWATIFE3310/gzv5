package gz.common.logic.billiards.nineball;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class NineBallGame extends BilliardsGame {

	private boolean nineBallPotted;

	public NineBallGame(BilliardsGameController controller, int[] sqc) {
		super(controller, sqc);
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		if (nineBallPotted) {
			if (cueBallPotted || firstBallFault) {
				currentState.getBall(9).restoreAtRandomPosition();
				setNextBall();
			} else {
				currentPlayer.addPottedBall(9);
				currentPlayer.incrementScore();
				gameOver = true;
				winner = currentPlayer.getIndex();
			}
		} else {
			changeTurn = true;
			
			if (cueBallPotted || firstBallFault) {
				for (int i = 0; i < pottedBalls.size(); i++) {
					int ballIndex = pottedBalls.get(i);
					currentState.getBall(ballIndex).restoreAtRandomPosition();
				}
			}
			else if (pottedBalls.size() > 0) {
				changeTurn = false;
				
				for (int i = 0; i < pottedBalls.size(); i++) {
					int ballIndex = pottedBalls.get(i);
					currentPlayer.addPottedBall(ballIndex);
				}
			}

			setNextBall();
		}
	}

	private void setNextBall() {
		int nextBallIndex = currentState.getLeastVisibleBall();
		for (int i = 0; i < players.length; i++)
			players[i].getBallSet()[0] = nextBallIndex;
	}

	@Override
	@SuppressWarnings("unused")
	protected void checkPottedBalls(BilliardsPlayer currentPlayer, int[] potteddBallIndices, int[] potteddBallSlots) {
		super.checkPottedBalls(currentPlayer, potteddBallIndices, potteddBallSlots);

		for (int i = 0; i < potteddBallIndices.length; i++) {
			int ballIndex = potteddBallIndices[i];
			int slotIndex = potteddBallSlots[i];

			if (ballIndex == 9) {
				nineBallPotted = true;
				break;
			}
		}
	}

	@Override
	public int[] initialBallSet() {
		return new int[] { 1 };
	}

	@Override
	public int initialHandBallMode() {
		return 2;
	}

	@Override
	protected void initializeBallPositions() {
		initialPositions[0] = new Point2D(58500, b_ct);

		int __reg19 = od + 130;
		int __reg18 = or + 70;
		int[][] __reg7 = new int[10][];
		__reg7[0] = null;
		__reg7[9] = new int[] { 22500, b_ct };
		__reg7[1] = new int[] { __reg7[9][0] + 3300, b_ct };
		__reg7[8] = new int[] { __reg7[9][0] - 3300, b_ct };
		__reg7[4] = new int[] { __reg7[9][0] - 1650, b_ct - __reg18 };
		__reg7[6] = new int[] { __reg7[9][0] + 1650, b_ct - __reg18 };
		__reg7[3] = new int[] { __reg7[9][0] - 1650, b_ct + __reg18 };
		__reg7[5] = new int[] { __reg7[9][0] + 1650, b_ct + __reg18 };
		__reg7[2] = new int[] { __reg7[9][0], b_ct - __reg19 };
		__reg7[7] = new int[] { __reg7[9][0], b_ct + __reg19 };
		int __reg6 = 1;
		while (__reg6 < __reg7.length) {
			initialPositions[__reg6] = new Point2D(__reg7[__reg6][0] + (sqc[__reg6] - 7) * 10, __reg7[__reg6][1] + (sqc[31 - __reg6] - 7) * 10);
			++__reg6;
		}
	}

	@Override
	protected void initializeOffsets() {
		bnm = 10;
		upk = 1;
		pw_cn = 3380;
		pw_sd = 4020;
		pdtf = 800;
		or = 920;
		ei = 8;
		bi = 7;
		whsm = 5;
	}

	@Override
	public void updateGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pocketedBallIndices, int[] pocketedBallSlots, int[] hitBallIndices) {
		nineBallPotted = false;

		super.updateGameState(cueBallIndex, ballIndices, ballPositions, pocketedBallIndices, pocketedBallSlots, hitBallIndices);
	}

}
