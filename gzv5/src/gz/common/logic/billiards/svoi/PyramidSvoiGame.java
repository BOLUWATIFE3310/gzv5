package gz.common.logic.billiards.svoi;

import java.util.ArrayList;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class PyramidSvoiGame extends BilliardsGame {

	public PyramidSvoiGame(BilliardsGameController controller, int[] sqc) {
		super(controller, sqc);
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		currentState.setHandBallMode(0);
		currentState.setSelecting(true);

		if (pottedBalls.size() > 0) {
			if (firstBallFault || !cueBallPotted) {
				changeTurn = true;
				
				for (int i = 0; i < pottedBalls.size(); i++) {
					int ballIndex = pottedBalls.get(i);
					getBall(ballIndex).restoreAtRandomPosition();
				}
				
				return;
			}

			changeTurn = false;
			currentPlayer.incrementScore(pottedBalls.size());
			for (int i = 0; i < pottedBalls.size(); i++) {
				int ballIndex = pottedBalls.get(i);
				currentPlayer.addPottedBall(ballIndex);
			}

			if (currentPlayer.getScore() >= 8) {
				gameOver = true;
				winner = currentPlayer.getIndex();
			}
		}
	}

	@Override
	public int[] initialBallSet() {
		return new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
	}

	@Override
	public int initialHandBallMode() {
		return 2;
	}

	@Override
	protected void initializeBallPositions() {
		initialPositions[0] = new Point2D(58500, b_ct);

		ArrayList<Integer> __reg3 = new ArrayList<>();
		int __reg6 = 1;
		while (__reg6 < bnm) {
			if (sqc[__reg6] > 8)
				__reg3.add(__reg6);
			else
				__reg3.add(0, __reg6);
			++__reg6;
		}
		int[] __reg13 = new int[] { 1, 2, 3, 4, 5 };
		int __reg20 = 24000;
		int __reg16 = b_ct;
		int __reg11 = od + 100;
		int __reg15 = 1780;
		int __reg17 = __reg13.length;
		int __reg4 = 0;
		int __reg12 = __reg20;
		__reg6 = 0;
		while (__reg6 < __reg17) {
			int __reg9 = __reg13[__reg6];
			int __reg8 = __reg16 - __reg9 * __reg11 / 2 + or;
			int __reg5 = 1;
			while (__reg5 <= __reg9) {
				initialPositions[__reg3.get(__reg4)] = new Point2D(__reg12 + (sqc[__reg4] - 7) * 10, __reg8 + (sqc[31 - __reg4] - 7) * 10);
				__reg8 = __reg8 + __reg11;
				++__reg4;
				++__reg5;
			}
			__reg12 = __reg12 - __reg15;
			++__reg6;
		}
	}

	@Override
	protected void initializeOffsets() {
		bnm = 16;
        upk = 1;
        pw_cn = 3000;
        pw_sd = 3300;
        pdtf = 400;
        or = 1000;
        ei = 8;
        bi = 7;
        whsm = 5;
	}

	@Override
	protected boolean resetCueBallWhenPotted() {
		return false;
	}

}
