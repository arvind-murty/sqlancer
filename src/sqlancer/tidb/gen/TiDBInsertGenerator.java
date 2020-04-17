package sqlancer.tidb.gen;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Query;
import sqlancer.QueryAdapter;
import sqlancer.Randomly;
import sqlancer.tidb.TiDBErrors;
import sqlancer.tidb.TiDBExpressionGenerator;
import sqlancer.tidb.TiDBProvider.TiDBGlobalState;
import sqlancer.tidb.TiDBSchema.TiDBColumn;
import sqlancer.tidb.TiDBSchema.TiDBDataType;
import sqlancer.tidb.TiDBSchema.TiDBTable;
import sqlancer.tidb.visitor.TiDBVisitor;

public class TiDBInsertGenerator {

	private final TiDBGlobalState globalState;
	private final Set<String> errors = new HashSet<>();
	private TiDBExpressionGenerator gen;

	public TiDBInsertGenerator(TiDBGlobalState globalState) {
		this.globalState = globalState;
		TiDBErrors.addInsertErrors(errors);
	}

	public static Query getQuery(TiDBGlobalState globalState) throws SQLException {
		return new TiDBInsertGenerator(globalState).get();
	}

	private Query get() {
		TiDBTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
		gen = new TiDBExpressionGenerator(globalState).setColumns(table.getColumns());
		StringBuilder sb = new StringBuilder();
		boolean isInsert = Randomly.getBoolean();
		if (isInsert) {
			sb.append("INSERT");
		} else {
			sb.append("REPLACE");
		}
		if (Randomly.getBooleanWithRatherLowProbability()) {
			sb.append(" ");
			sb.append(Randomly.fromOptions("LOW_PRIORITY", "HIGH_PRIORITY", "DELAYED"));
		}
		if (isInsert && Randomly.getBoolean()) {
			if (table.getColumns().stream().anyMatch(c -> c.getType().getPrimitiveDataType() == TiDBDataType.DECIMAL)) {
				/* https://github.com/pingcap/tidb/issues/16025 */
				throw new IgnoreMeException();
			}
			sb.append(" IGNORE ");
		}
		sb.append(" INTO ");
		sb.append(table.getName());
		if (Randomly.getBoolean()) {
			sb.append(" VALUES ");
			List<TiDBColumn> columns = table.getColumns();
			insertColumns(sb, columns);
		} else {
			List<TiDBColumn> columnSubset = table.getRandomNonEmptyColumnSubset();
			sb.append("(");
			sb.append(columnSubset.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
			sb.append(") VALUES ");
			insertColumns(sb, columnSubset);
		}
		if (isInsert && Randomly.getBoolean()) {
			sb.append(" ON DUPLICATE KEY UPDATE ");
			sb.append(table.getRandomColumn().getName());
			sb.append("=");
			sb.append(TiDBVisitor.asString(gen.generateExpression()));
		}
		return new QueryAdapter(sb.toString(), errors);
	}

	private void insertColumns(StringBuilder sb, List<TiDBColumn> columns) {
		for (int nrRows = 0; nrRows < Randomly.smallNumber() + 1; nrRows++) {
			if (nrRows != 0) {
				sb.append(", ");
			}
			sb.append("(");
			for (int nrColumn = 0; nrColumn < columns.size(); nrColumn++) {
				if (nrColumn != 0) {
					sb.append(", ");
				}
				insertValue(sb, columns.get(nrColumn));
			}
			sb.append(")");
		}
	}

	private void insertValue(StringBuilder sb, TiDBColumn tiDBColumn) {
		sb.append(gen.generateConstant()); // TODO: try to insert valid data
	}

}
