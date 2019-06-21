package lama.mysql.gen;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.mysql.MySQLSchema;
import lama.mysql.MySQLSchema.MySQLTable;

public class MySQLAlterTable {

	private final MySQLSchema schema;
	private final Randomly r;
	private final StringBuilder sb = new StringBuilder();
	boolean couldAffectSchema;
	private List<Action> selectedActions;

	public MySQLAlterTable(MySQLSchema newSchema, Randomly r) {
		this.schema = newSchema;
		this.r = r;
	}

	public static Query create(MySQLSchema newSchema, Randomly r) {
		return new MySQLAlterTable(newSchema, r).create();
	}

	private enum Action {
		ALGORITHM, CHECKSUM, COMPRESSION, DISABLE_ENABLE_KEYS,
		DROP_COLUMN("Cannot drop column", "ALGORITHM=INPLACE is not supported.", "ALGORITHM=INSTANT is not supported.",
				"Duplicate entry", "A primary key index cannot be invisible" /* this error should not occur, see https://bugs.mysql.com/bug.php?id=95897 */, "Field in list of fields for partition function not found in table", "in 'partition function'"),
		FORCE, DELAY_KEY_WRITE, INSERT_METHOD, ROW_FORMAT, STATS_AUTO_RECALC, STATS_PERSISTENT, PACK_KEYS, RENAME("doesn't exist", "already exists"),
		DROP_PRIMARY_KEY(
				"ALGORITHM=INSTANT is not supported. Reason: Dropping a primary key is not allowed without also adding a new primary key. Try ALGORITHM=COPY/INPLACE.");

		private String[] potentialErrors;

		private Action(String... couldCauseErrors) {
			this.potentialErrors = couldCauseErrors;
		}

	}

	private Query create() {
		sb.append("ALTER TABLE ");
		MySQLTable table = schema.getRandomTable();
		sb.append(table.getName());
		sb.append(" ");
		List<Action> list = new ArrayList<>(Arrays.asList(Action.values()));
		if (!table.hasPrimaryKey() || true /* https://bugs.mysql.com/bug.php?id=95894 */) {
			list.remove(Action.DROP_PRIMARY_KEY);
		}
		if (table.getColumns().size() == 1) {
			list.remove(Action.DROP_COLUMN);
		}
		selectedActions = Randomly.subset(list);
		int i = 0;
		for (Action a : selectedActions) {
			if (i++ != 0) {
				sb.append(", ");
			}
			switch (a) {
			case ALGORITHM:
				sb.append("ALGORITHM ");
				sb.append(Randomly.fromOptions("INSTANT", "INPLACE", "COPY", "DEFAULT"));
				break;
			case CHECKSUM:
				sb.append("CHECKSUM ");
				sb.append(Randomly.fromOptions(0, 1));
				break;
			case COMPRESSION:
				sb.append("COMPRESSION ");
				sb.append("'");
				sb.append(Randomly.fromOptions("ZLIB", "LZ4", "NONE"));
				sb.append("'");
				break;
			case DELAY_KEY_WRITE:
				sb.append("DELAY_KEY_WRITE ");
				sb.append(Randomly.fromOptions(0, 1));
				break;
			case DROP_COLUMN:
				sb.append("DROP ");
				if (Randomly.getBoolean()) {
					sb.append("COLUMN ");
				}
				sb.append(table.getRandomColumn().getName());
				couldAffectSchema = true;
				break;
			case DISABLE_ENABLE_KEYS:
				sb.append(Randomly.fromOptions("DISABLE", "ENABLE"));
				sb.append(" KEYS");
				break;
			case DROP_PRIMARY_KEY:
				assert table.hasPrimaryKey();
				sb.append("DROP PRIMARY KEY");
				couldAffectSchema = true;
				break;
			case FORCE:
				sb.append("FORCE");
				break;
			case INSERT_METHOD:
				sb.append("INSERT_METHOD ");
				sb.append(Randomly.fromOptions("NO", "FIRST", "LAST"));
				break;
			case ROW_FORMAT:
				sb.append("ROW_FORMAT ");
				sb.append(Randomly.fromOptions("DEFAULT", "DYNAMIC", "FIXED", "COMPRESSED", "REDUNDANT", "COMPACT"));
				break;
			case STATS_AUTO_RECALC:
				sb.append("STATS_AUTO_RECALC ");
				sb.append(Randomly.fromOptions(0, 1, "DEFAULT"));
				break;
			case STATS_PERSISTENT:
				sb.append("STATS_PERSISTENT ");
				sb.append(Randomly.fromOptions(0, 1, "DEFAULT"));
				break;
			case PACK_KEYS:
				sb.append("PACK_KEYS ");
				sb.append(Randomly.fromOptions(0, 1, "DEFAULT"));
				break;
			case RENAME:
				sb.append("RENAME ");
				if (Randomly.getBoolean()) {
					sb.append(Randomly.fromOptions("TO", "AS"));
					sb.append(" ");
				}
				sb.append("t" + Randomly.smallNumber());
				couldAffectSchema = true;
				break;
			}
		}
		if (Randomly.getBooleanWithSmallProbability()) {
			if (i != 0) {
				sb.append(", ");
			}
			// should be given as last option
			sb.append(" ORDER BY ");
			sb.append(table.getRandomNonEmptyColumnSubset().stream().map(c -> c.getName())
					.collect(Collectors.joining(", ")));
		}
		// TODO Auto-generated method stub
		return new QueryAdapter(sb.toString()) {
			public void execute(java.sql.Connection con) throws java.sql.SQLException {
				try {
					super.execute(con);
				} catch (SQLException e) {
					if (errorIsExpected(e.getMessage())) {
						// ignore
					} else if (e.getMessage().contains("does not support the create option")) {
						// ignore
					} else if (e.getMessage().contains("doesn't have this option")) {
						// ignore
					} else if (e.getMessage().contains("is not supported for this operation")) {
						// ignore
					} else {
						throw e;
					}
				}

			};

			private boolean errorIsExpected(String errorMessage) {
				for (Action a : selectedActions) {
					for (String error : a.potentialErrors) {
						if (errorMessage.contains(error)) {
							return true;
						}
					}
				}
				return false;
			}

			@Override
			public boolean couldAffectSchema() {
				return couldAffectSchema;
			}

		};
	}

}