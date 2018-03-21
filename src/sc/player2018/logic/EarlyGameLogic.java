package sc.player2018.logic;

import java.util.ArrayList;

import sc.plugin2018.CardType;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;
import sc.plugin2018.Player;

public class EarlyGameLogic {
	static int SALAD_FIELD = 10;

	public static Move getTurn(GameState gameState) {
		Player currentPlayer = gameState.getCurrentPlayer();
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();
		MoveList baseList = new MoveList(possibleMoves,gameState);

		if (currentPlayer.getSalads() == 5) {
			// let's waste some turns in the beginning to lose a salad and wait for the
			// enemy to move away
			Move selectedMove = baseList.select(CardType.EAT_SALAD).getNearest();
			if (selectedMove != null) {
				return selectedMove;
			}
		} else {
			// can we move to the next salad field?
			if (gameState.isOccupied(SALAD_FIELD)) {
				// no we can't
				Move selectedMove = baseList.getNearest();
				if (selectedMove != null) {
					return selectedMove;
				}
			} else {
				// move to the salad field
				Move selectedMove = baseList.select(FieldType.SALAD).getNearest();
				if (selectedMove != null) {
					return selectedMove;
				}

			}
		}

		return null;
	}
}
