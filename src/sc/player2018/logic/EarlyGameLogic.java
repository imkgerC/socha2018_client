package sc.player2018.logic;

import java.util.ArrayList;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;
import sc.plugin2018.Player;

public class EarlyGameLogic {
	static int SALAD_FIELD = 10;

	public static Move getTurn(GameState gameState) {
		Player currentPlayer = gameState.getCurrentPlayer();
		int currentIndex = currentPlayer.getFieldIndex();
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();

		if (currentPlayer.getSalads() == 5) {
			// let's waste some turns in the beginning to lose a salad and wait for the
			// enemy to move away
			Move selectedMove = LogicHelper.getHareEatSalad(possibleMoves, gameState, currentIndex);
			if (selectedMove != null) {
				return selectedMove;
			}
		} else {
			// can we move to the next salad field?
			if (gameState.isOccupied(SALAD_FIELD)) {
				// no we can't
				Move selectedMove = LogicHelper.getNextAdvance(possibleMoves);
				if (selectedMove != null) {
					return selectedMove;
				}
			} else {
				// move to the salad field
				Move selectedMove = LogicHelper.getNextByType(FieldType.SALAD, possibleMoves, gameState, currentIndex);
				if (selectedMove != null) {
					return selectedMove;
				}

			}
		}

		return null;
	}
}
