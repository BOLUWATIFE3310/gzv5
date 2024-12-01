package gz.common.logic.billiards.snooker;

import java.util.ArrayList;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class SnookerGame extends BilliardsGame {

	private static final int[] BALL_SCORE = { 0, 2, 3, 4, 5, 6, 7, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };

	private ArrayList<Integer> redBalls;
	private ArrayList<Integer> coloredBalls;
	private boolean pottedOtherBall;

	public SnookerGame(BilliardsGameController controller, int[] sqc) {
		super(controller, sqc);

		redBalls = new ArrayList<>();
		coloredBalls = new ArrayList<>();
	}

	@Override
	protected boolean beforeStart() {
		if (!super.beforeStart())
			return false;

		for (BilliardsPlayer player : players)
			player.setBallIcon(9);

		redBalls.clear();
		for (int i = 7; i < 22; i++)
			redBalls.add(i);

		coloredBalls.clear();
		for (int i = 1; i < 7; i++)
			coloredBalls.add(i);

		return true;
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		if (currentState.getHandBallMode() == 1)
			currentState.setHandBallMode(3);

		BilliardsPlayer opponentPlayer = getPlayer(1 - currentPlayer.getIndex());

		if (firstBallFault || cueBallPotted || pottedOtherBall) {
			changeTurn = true;

			if (!cueBallPotted)
				currentState.setHandBallMode(0);

			int fault = 0;
			if (firstBallFault || cueBallPotted)
				fault = 4;

			for (int i = 0; i < pottedBalls.size(); i++) {
				int ballIndex = pottedBalls.get(i);
				if (ballIndex >= 7) {
					if (ballIndex >= 7) {
						if (fault < 7)
							fault = 7;
					}
					else if (fault < BALL_SCORE[ballIndex])
						fault = BALL_SCORE[ballIndex];

					opponentPlayer.addPottedBall(ballIndex);
				} else {
					if (fault < BALL_SCORE[ballIndex])
						fault = BALL_SCORE[ballIndex];
					
					coloredBalls.add(ballIndex);
					currentState.getBall(ballIndex).reset();
				}
			}
			
			opponentPlayer.incrementScore(fault);
		} else
			for (int i = 0; i < pottedBalls.size(); i++) {
				int ballIndex = pottedBalls.get(i);
				if (ballIndex < 7 && (redBalls.size() > 0 || currentPlayer.getBallIcon() >= 9)) {
					coloredBalls.add(ballIndex);
					currentState.getBall(ballIndex).reset();
				}

				currentPlayer.addPottedBall(ballIndex);
				currentPlayer.incrementScore(BALL_SCORE[ballIndex]);
			}

		if (changeTurn) {
			if (redBalls.size() > 0) {
				opponentPlayer.setBallIcon(9);
				opponentPlayer.setBallSet(redBalls);
			} else {
				int ballIndex = currentState.getLeastVisibleBall();
				opponentPlayer.setBallIcon(ballIndex + 2);
				opponentPlayer.setBallSet(new int[] { ballIndex });
			}
		} else {
			int ballIcon = currentPlayer.getBallIcon();
			if (ballIcon == 9) {
				currentPlayer.setBallIcon(10);
				currentPlayer.setBallSet(coloredBalls);
			} else if (ballIcon == 10) {
				if (redBalls.size() == 0) {
					int ballIndex = currentState.getLeastVisibleBall();
					currentPlayer.setBallIcon(ballIndex + 2);
					currentPlayer.setBallSet(new int[] { ballIndex });
				} else {
					currentPlayer.setBallIcon(9);
					currentPlayer.setBallSet(redBalls);
				}
			} else {
				int ballIndex = currentState.getLeastVisibleBall();
				currentPlayer.setBallIcon(ballIndex + 2);
				currentPlayer.setBallSet(new int[] { ballIndex });
			}

		}

		if (coloredBalls.size() == 0 && redBalls.size() == 0) {
			if (currentPlayer.getScore() > opponentPlayer.getScore()) {
				gameOver = true;
				winner = currentPlayer.getIndex();
			}
			else if (currentPlayer.getScore() < opponentPlayer.getScore()) {
				gameOver = true;
				winner = opponentPlayer.getIndex();
			}
			else {
				changeTurn = false;
				currentState.getBall(6).restoreAtRandomPosition();
				currentPlayer.setBallIcon(8);
				currentPlayer.setBallSet(new int[] { 6 });
			}
		}
	}

	@Override
	@SuppressWarnings("unused")
	protected void checkPottedBalls(BilliardsPlayer currentPlayer, int[] pottedBallIndices, int[] pottedBallSlots) {
		for (int i = 0; i < pottedBallIndices.length; i++) {
			int ballIndex = pottedBallIndices[i];
			if (ballIndex < 0 || ballIndex > bnm)
				continue;

			int slotIndex = pottedBallSlots[i];

			if (ballIndex >= 1 && ballIndex <= 6)
				coloredBalls.remove((Object) ballIndex);
			else if (ballIndex >= 7 && ballIndex <= 21)
				redBalls.remove((Object) ballIndex);
		}

		super.checkPottedBalls(currentPlayer, pottedBallIndices, pottedBallSlots);
	}

	@Override
	public int[] initialBallSet() {
		return new int[] { 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21 };
	}

	@Override
	public int initialHandBallMode() {
		return 3;
	}

	@Override
	protected void initializeBallPositions() {
		int __reg11 = od + 90;
		int __reg20 = 24000;
		int __reg12 = __reg20;
		int __reg4 = 7;

		int[] __reg13 = new int[] { 1, 2, 3, 4, 5 };
		for (int __reg6 = 0; __reg6 < __reg13.length; __reg6++) {
			int __reg9 = __reg13[__reg6];
			int __reg8 = b_ct - __reg9 * __reg11 / 2 + or;
			int __reg5 = 1;
			while (__reg5 <= __reg9) {
				initialPositions[__reg4] = new Point2D(__reg12 + (sqc[__reg4] - 7) * 10, __reg8 + (sqc[31 - __reg4] - 7) * 10);
				__reg8 = __reg8 + __reg11;
				++__reg4;
				++__reg5;
			}

			__reg12 = __reg12 - 1500;
		}

		initialPositions[0] = new Point2D(61500, b_ct);
		initialPositions[1] = new Point2D(x_offs + 55500, b_ct - 6000);
		initialPositions[2] = new Point2D(x_offs + 55500, b_ct + 6000);
		initialPositions[3] = new Point2D(x_offs + 55500, b_ct);
		initialPositions[4] = new Point2D(a_ct, b_ct);
		initialPositions[5] = new Point2D(initialPositions[7].getX() + od + 200, b_ct);
		initialPositions[6] = new Point2D(x_offs + 8000, b_ct);
	}

	@Override
	protected void initializeOffsets() {
		upk = 1;
		pw_cn = 2980;
		pw_sd = 4260;
		pdtf = 400;
		or = 840;
		ei = 8;
		bi = 7;
		bnm = 22;
		whsm = 6;
	}

	@Override
	protected void onPottedMyBall(BilliardsPlayer currentPlayer, int ballIndex) {
		changeTurn = false;
	}

	@Override
	protected void onPottedOtherBall(BilliardsPlayer currentPlayer, int ballIndex) {
		pottedOtherBall = true;
	}

	@Override
	public void updateGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pocketedBallIndices, int[] pocketedBallSlots, int[] hitBallIndices) {
		pottedOtherBall = false;

		super.updateGameState(cueBallIndex, ballIndices, ballPositions, pocketedBallIndices, pocketedBallSlots, hitBallIndices);
	}

}
