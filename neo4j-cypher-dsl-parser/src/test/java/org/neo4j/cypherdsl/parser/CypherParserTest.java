/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypherdsl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.cypherdsl.core.AliasedExpression;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.RelationshipPattern;

/**
 * @author Michael J. Simons
 */
class CypherParserTest {

	@Nested
	class RelationshipPatterns {

		@ParameterizedTest
		@CsvSource(nullValues = "N/A", value = { "N/A, N/A", "N/A, 5", "5, N/A", "5, 10", "-,-" })
		void simplePatternWithVariousLengths(String minimum, String maximum) {
			StringBuilder simplePattern = new StringBuilder("(n)-");
			if (minimum != null || maximum != null) {
				simplePattern.append("[*");
				if (minimum != null && !"-".equals(minimum)) {
					simplePattern.append(minimum);
				}
				if (!"-".equals(maximum)) {
					simplePattern.append("..");
					if (maximum != null) {
						simplePattern.append(maximum);
					}
				}
				simplePattern.append("]");
			}
			simplePattern.append("->(m)");
			var rel = CypherParser.parseRelationship(simplePattern.toString());
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo(String.format("MATCH %s RETURN *", simplePattern));
		}

		@ParameterizedTest
		@ValueSource(strings = { "T", "T1|T2", "T1|T2|T3" })
		void types(String types) {
			var rel = CypherParser.parseRelationship(String.format("(n)-[:%s]->(m)", types));
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo(String.format("MATCH (n)-[:%s]->(m) RETURN *",
					Arrays.stream(types.split("\\|")).map(v -> String.format("`%s`", v))
						.collect(Collectors.joining("|"))));
		}

		@ParameterizedTest
		@CsvSource({ "-,-", "<-,-", "-,->" })
		void direction(String left, String right) {
			StringBuilder simplePattern = new StringBuilder("(n)")
				.append(left)
				.append(right)
				.append("(m)");
			var rel = CypherParser.parseRelationship(simplePattern.toString());
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo(String.format("MATCH %s RETURN *", simplePattern));
		}

		@Test
		void pointyThingAtBothSidesIsNotSupported() {
			assertThatIllegalArgumentException().isThrownBy(() -> CypherParser.parseRelationship("(n)<-->(m)"))
				.withMessage("Only left-to-right, right-to-left or unidirectional path elements are supported.");
		}

		@Test
		void chain() {
			RelationshipPattern rel = CypherParser.parseRelationship("(n)-->()-->(o)");
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo("MATCH (n)-->()-->(o) RETURN *");
		}

		@Test
		void shortestPath() {
			Expression ex = CypherParser.parseExpression("shortestPath((n:A)-->(o:B))");
			assertThat(Cypher.returning(ex).build().getCypher())
				.isEqualTo("RETURN shortestPath((n:`A`)-->(o:`B`))");
		}

		@Test
		void allShortestPaths() {
			Expression ex = CypherParser.parseExpression("allShortestPaths((n:A)-->(o:B))");
			assertThat(Cypher.returning(ex).build().getCypher())
				.isEqualTo("RETURN allShortestPaths((n:`A`)-->(o:`B`))");
		}

		@Test
		void names() {
			RelationshipPattern rel = CypherParser.parseRelationship("(n)-[r1]->()-[r2]->(o)");
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo("MATCH (n)-[r1]->()-[r2]->(o) RETURN *");
		}

		@Test
		void properties() {
			RelationshipPattern rel = CypherParser.parseRelationship("(n)-[{a: 'b', c: 'd'}]->(o)");
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo("MATCH (n)-[ {a: 'b', c: 'd'}]->(o) RETURN *");
		}

		@Test
		void propertiesOnAChain() {
			RelationshipPattern rel = CypherParser
				.parseRelationship("(n)-[r:TYPE{x:'x'}]->(m)-[{a: 'b', c: 'd'}]->(o)");
			assertThat(Cypher.match(rel).returning(Cypher.asterisk()).build().getCypher())
				.isEqualTo("MATCH (n)-[r:`TYPE` {x: 'x'}]->(m)-[ {a: 'b', c: 'd'}]->(o) RETURN *");
		}
	}

	@Test
	void shouldProvideANiceErrorMessage() {

		assertThatExceptionOfType(UnsupportedCypherException.class)
			.isThrownBy(() -> CypherParser.parse("CREATE ROLE myrole"))
			.withMessage("You used one Cypher construct not yet supported by the Cypher-DSL:\n"
				+ "\n"
				+ "\tCREATE ROLE myrole\n"
				+ "\n"
				+ "Feel free to open an issue so that we might add support for it at https://github.com/neo4j-contrib/cypher-dsl/issues/new");
	}

	@Test
	void shouldParseCount() {
		assertExpression("Count(*)", "count(*)");
	}

	@Test
	void shouldParseIn() {
		assertExpression("n in [1,2,3]", "n IN [1, 2, 3]");
	}

	@ParameterizedTest
	@CsvSource(value = {
		"f()| f()",
		"foo.bar()| foo.bar()",
		"foo.bar(e)| foo.bar(e)",
		"foo.bar(e,f)| foo.bar(e, f)",
		"count(distinct e,f)| count(DISTINCT e, f)"
	}, delimiterString = "|")
	void shouldParseFunctionInvocation(String input, String expected) {
		assertExpression(input, expected);
	}

	@Nested
	class Literals {

		@Test
		void shouldParseListLiteral() {
			assertExpression("[1,2,a, 'b']", "[1, 2, a, 'b']");
		}

		@Test
		void shouldParseLookups() {
			assertExpression("n[23]", "n[23]");
		}
	}

	@Nested
	class Parameters {

		@Test
		void newParameterShouldWork() {
			assertExpression("$foo", "$foo");
		}

		@Test
		void newNumberedParameterShouldWork() {
			assertExpression("$1", "$1");
		}
	}

	static void assertExpression(String expression) {
		assertExpression(expression, expression);
	}

	static void assertExpression(String expression, String expected) {

		Expression e = CypherParser.parseExpression(expression);
		assertThat(Cypher.returning(e).build().getCypher())
			.isEqualTo(String.format("RETURN %s", expected));
	}

	static void assertNode(Node node, String cypherDslRepresentation) {

		assertThat(Cypher.match(node).returning(Cypher.asterisk()).build().getCypher())
			.isEqualTo(String.format("MATCH %s RETURN *", cypherDslRepresentation));
	}

	@Nested
	class Predicates {

		@Test
		void any() {

			assertThatIllegalArgumentException()
				.isThrownBy(() -> CypherParser.parseExpression("any(color IN n.liked_colors)"))
				.withMessage("any(...) requires a WHERE predicate");
			assertExpression("any(color IN n.liked_colors WHERE color = 'yellow')");
		}

		@Test
		void none() {

			assertThatIllegalArgumentException()
				.isThrownBy(() -> CypherParser.parseExpression("none(color IN n.liked_colors)"))
				.withMessage("none(...) requires a WHERE predicate");
			assertExpression("none(color IN n.liked_colors WHERE color = 'yellow')");
		}

		@Test
		void single() {

			assertThatIllegalArgumentException()
				.isThrownBy(() -> CypherParser.parseExpression("single(color IN n.liked_colors)"))
				.withMessage("single(...) requires a WHERE predicate");
			assertExpression("single(color IN n.liked_colors WHERE color = 'yellow')");
		}

		@Test
		void all() {

			assertThatIllegalArgumentException()
				.isThrownBy(() -> CypherParser.parseExpression("all(color IN n.liked_colors)"))
				.withMessage("all(...) requires a WHERE predicate");
			assertExpression("all(color IN n.liked_colors WHERE color = 'yellow')");
		}
	}

	@Test
	void onNewReturnItemCallbacksShouldBeApplied() {

		var cnt = new AtomicInteger(0);
		var options = Options.newOptions()
			.withCallback(ExpressionCreatedEventType.ON_RETURN_ITEM, AliasedExpression.class, e -> {
				assertThat(cnt.compareAndSet(0, 1)).isTrue();
				if (e instanceof AliasedExpression) {
					return (AliasedExpression) e;
				}
				return e.as("foo");
			})
			.withCallback(ExpressionCreatedEventType.ON_RETURN_ITEM, Expression.class, e -> {
				assertThat(cnt.compareAndSet(1, 2)).isTrue();
				return e;
			}).build();

		var statement = CypherParser.parseStatement("RETURN 1", options);
		assertThat(cnt.get()).isEqualTo(2);
		assertThat(statement.getCypher()).isEqualTo("RETURN 1 AS foo");
	}

	@Test
	void shouldParseReturnAll() {
		var statement1 = CypherParser.parseStatement("WITH 1 AS foo WITH *, 2 AS bar RETURN *");
		assertThat(statement1.getCypher()).isEqualTo("WITH 1 AS foo WITH *, 2 AS bar RETURN *");

		var statement2 = CypherParser.parseStatement("WITH 1 AS foo WITH *, 2 AS bar RETURN foo");
		assertThat(statement2.getCypher()).isEqualTo("WITH 1 AS foo WITH *, 2 AS bar RETURN foo");
	}

	@ParameterizedTest
	@ValueSource(strings = { "CREATE", "MERGE", "MATCH" })
	void patternElementCallBacksShouldBeApplied(String clause) {

		var builder = Options.newOptions();
		EnumSet.allOf(PatternElementCreatedEventType.class)
			.forEach(et ->
				builder.withCallback(et, patternElement -> {
						if (patternElement instanceof Node) {
							var existing = ((Node) patternElement).getLabels().get(0).getValue();
							return Cypher.node(existing, "FirstLabelAdded");
						}
						return patternElement;
					})
					.withCallback(et, patternElement -> {
						if (patternElement instanceof Node) {
							var l1 = ((Node) patternElement).getLabels().get(0).getValue();
							var l2 = ((Node) patternElement).getLabels().get(1).getValue();
							return Cypher.node(l1, l2, "SecondLabelAdded");
						}
						return patternElement;
					})
			);

		var options = builder.build();
		var statement = CypherParser.parseStatement(clause + " (n:Movie) RETURN n", options);
		assertThat(statement.getCypher()).isEqualTo(
			clause + " (:`Movie`:`FirstLabelAdded`:`SecondLabelAdded`) RETURN n");
	}

	@Test
	void shouldNotAllowInvalidCallbacks() {

		assertThatIllegalArgumentException().isThrownBy(() ->
			Options.newOptions()
				.withCallback(ExpressionCreatedEventType.ON_SET_PROPERTY, Expression.class, e -> e).build()
		).withMessage(
			"The type that is produced by 'ON_SET_PROPERTY' is not compatible with interface org.neo4j.cypherdsl.core.Expression");
	}

	private static Stream<Arguments> inputAndIdentifiableExpressions() {
		return Stream.of(
			Arguments.of("""
				CALL {
					MATCH (p:Person)-[:LIKES]->(:Technology {type: "Java"})
					RETURN p

					UNION

					MATCH (p:Person)
					WHERE size((p)-[:IS_FRIENDS_WITH]->()) > 1
					RETURN p
				}
				RETURN p.name AS person, p.birthdate AS dob
				ORDER BY dob DESC""",
				List.of("person", "dob")
			),
			Arguments.of("""
				MATCH p=(start)-[*]->(finish)
				WHERE start.name = 'A' AND finish.f = 'D'
				FOREACH (n IN nodes(p) | SET n.marked = true)""",
				List.of("finish", "start", "p")
			),
			Arguments.of("""
				MATCH (a)
				WHERE a.name = 'Eskil'
				RETURN a.array, [x IN a.array WHERE size(x) = 3]""",
				List.of("a.array")
			)
		);
	}

	@ParameterizedTest
	@MethodSource("inputAndIdentifiableExpressions")
	void parseAndIdentifyShouldWork(String cypher, List<String> expected) {

		var identifiables = CypherParser.parse(cypher).getIdentifiableExpressions();
		assertThat(identifiables.stream().map(Cypher::format))
			.containsExactlyInAnyOrderElementsOf(expected);
	}
}
