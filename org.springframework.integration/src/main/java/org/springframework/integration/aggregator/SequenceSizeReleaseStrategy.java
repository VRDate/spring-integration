/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.aggregator;

/**
 * An implementation of {@link ReleaseStrategy} that simply compares the
 * current size of the message list to the expected 'sequenceSize'.
 * 
 * @author Mark Fisher
 * @author Marius Bogoevici
 * @author Dave Syer
 */
public class SequenceSizeReleaseStrategy implements ReleaseStrategy {

	public boolean canRelease(MessageGroup messages) {
		return messages.isComplete();
	}

}