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

import React from 'react';

import { ExternalLink, Field } from '../components';
import { getLink } from '../links';
import { deepGet, EMPTY_ARRAY, oneOf } from '../utils';

import { IngestionSpec } from './ingestion-spec';

export type DruidFilter = Record<string, any>;

export interface DimensionFiltersWithRest {
  dimensionFilters: DruidFilter[];
  restFilter?: DruidFilter;
}

export function splitFilter(filter: DruidFilter | null): DimensionFiltersWithRest {
  const inputAndFilters: DruidFilter[] = filter
    ? filter.type === 'and' && Array.isArray(filter.fields)
      ? filter.fields
      : filter.type !== 'true'
      ? [filter]
      : EMPTY_ARRAY
    : EMPTY_ARRAY;
  const dimensionFilters: DruidFilter[] = inputAndFilters.filter(
    f => typeof f.dimension === 'string',
  );
  const restFilters: DruidFilter[] = inputAndFilters.filter(f => typeof f.dimension !== 'string');

  return {
    dimensionFilters,
    restFilter: restFilters.length
      ? restFilters.length > 1
        ? { type: 'and', filters: restFilters }
        : restFilters[0]
      : undefined,
  };
}

export function joinFilter(
  dimensionFiltersWithRest: DimensionFiltersWithRest,
): DruidFilter | undefined {
  const { dimensionFilters, restFilter } = dimensionFiltersWithRest;
  let newFields = dimensionFilters || EMPTY_ARRAY;
  if (restFilter && restFilter.type) newFields = newFields.concat([restFilter]);

  if (!newFields.length) return;
  if (newFields.length === 1) return newFields[0];
  return { type: 'and', fields: newFields };
}

export const FILTER_FIELDS: Field<DruidFilter>[] = [
  {
    name: 'type',
    type: 'string',
    suggestions: ['selector', 'in', 'regex', 'like', 'not'],
  },
  {
    name: 'dimension',
    type: 'string',
    defined: (df: DruidFilter) => oneOf(df.type, 'selector', 'in', 'regex', 'like'),
  },
  {
    name: 'value',
    type: 'string',
    defined: (df: DruidFilter) => df.type === 'selector',
  },
  {
    name: 'values',
    type: 'string-array',
    defined: (df: DruidFilter) => df.type === 'in',
  },
  {
    name: 'pattern',
    type: 'string',
    defined: (df: DruidFilter) => oneOf(df.type, 'regex', 'like'),
  },

  {
    name: 'field.type',
    label: 'Sub-filter type',
    type: 'string',
    suggestions: ['selector', 'in', 'regex', 'like'],
    defined: (df: DruidFilter) => df.type === 'not',
  },
  {
    name: 'field.dimension',
    label: 'Sub-filter dimension',
    type: 'string',
    defined: (df: DruidFilter) => df.type === 'not',
  },
  {
    name: 'field.value',
    label: 'Sub-filter value',
    type: 'string',
    defined: (df: DruidFilter) => df.type === 'not' && deepGet(df, 'field.type') === 'selector',
  },
  {
    name: 'field.values',
    label: 'Sub-filter values',
    type: 'string-array',
    defined: (df: DruidFilter) => df.type === 'not' && deepGet(df, 'field.type') === 'in',
  },
  {
    name: 'field.pattern',
    label: 'Sub-filter pattern',
    type: 'string',
    defined: (df: DruidFilter) =>
      df.type === 'not' && oneOf(deepGet(df, 'field.type'), 'regex', 'like'),
  },
];

export const FILTERS_FIELDS: Field<IngestionSpec>[] = [
  {
    name: 'spec.dataSchema.granularitySpec.intervals',
    label: 'Time intervals',
    type: 'string-array',
    placeholder: 'ex: 2020-01-01/2020-06-01',
    info: (
      <>
        <p>A comma separated list of intervals for the raw data being ingested.</p>
        <p>
          Explicitly specifying the list of intervals contained in the data will make some ingestion
          jobs run faster.
        </p>
      </>
    ),
  },
  {
    name: 'spec.dataSchema.transformSpec.filter',
    label: 'Filter',
    type: 'json',
    height: '350px',
    placeholder: '{ "type": "true" }',
    info: (
      <>
        <p>
          A Druid{' '}
          <ExternalLink href={`${getLink('DOCS')}/querying/filters.html`}>
            JSON filter expression
          </ExternalLink>{' '}
          to apply to the data.
        </p>
        <p>
          Note that only the value that match the filter will be included. If you want to remove
          some data values you must negate the filter.
        </p>
      </>
    ),
  },
];
