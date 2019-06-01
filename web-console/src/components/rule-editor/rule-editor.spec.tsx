/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from 'react';
import { render } from 'react-testing-library';

import {Rule, RuleEditor} from './rule-editor';

describe('rule editor', () => {
  it('rule editor snapshot', () => {
    const ruleEditor =
    <RuleEditor
      rule={{type: 'loadForever' }}
      tiers={['test', 'test', 'test']}
      onChange={(newRule: Rule) => null}
      onDelete={() => null}
      moveUp={null}
      moveDown={null}
    />;
    const { container, getByText } = render(ruleEditor);
    expect(container.firstChild).toMatchSnapshot();
  });
});
