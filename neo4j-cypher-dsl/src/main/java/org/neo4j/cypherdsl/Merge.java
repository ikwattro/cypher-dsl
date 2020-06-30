/*
 * Copyright (c) 2019-2020 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypherdsl;

import static org.apiguardian.api.API.Status.*;

import org.apiguardian.api.API;
import org.neo4j.cypherdsl.support.Visitor;

/**
 * See <a href="https://s3.amazonaws.com/artifacts.opencypher.org/railroad/Create.html">Create</a>.
 *
 * @author Michael J. Simons
 * @soundtrack Die Ärzte - Seitenhirsch
 * @since 1.0
 */
@API(status = EXPERIMENTAL, since = "1.0")
public final class Merge implements UpdatingClause {

	private final Pattern pattern;

	Merge(Pattern pattern) {
		this.pattern = pattern;
	}

	@Override
	public void accept(Visitor visitor) {

		visitor.enter(this);
		this.pattern.accept(visitor);
		visitor.leave(this);
	}
}

