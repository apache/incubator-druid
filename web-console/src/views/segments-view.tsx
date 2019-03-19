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

import { Button, Intent } from "@blueprintjs/core";
import axios from 'axios';
import * as classNames from 'classnames';
import * as React from 'react';
import ReactTable from "react-table";
import { Filter } from "react-table";

import { H5, IconNames } from "../components/filler";
import { TableColumnSelection } from "../components/table-column-selection";
import { AppToaster } from "../singletons/toaster";
import {
  addFilter,
  formatBytes,
  formatNumber,
  makeBooleanFilter,
  parseList,
  queryDruidSql,
  QueryManager, TableColumnSelectionHandler
} from "../utils";

import "./segments-view.scss";

const segmentTableColumnSelection = "segment-table-column-selection";
const tableColumns: string[] = ["Segment ID", "Datasource", "Start", "End", "Version", "Partition",
  "Size", "Num rows", "Replicas", "Is published", "Is realtime", "Is available"];

export interface SegmentsViewProps extends React.Props<any> {
  goToSql: (initSql: string) => void;
  datasource: string | null;
  onlyUnavailable: boolean | null;
}

export interface SegmentsViewState {
  segmentsLoading: boolean;
  segments: any[] | null;
  segmentsError: string | null;
  segmentFilter: Filter[];
}

interface QueryAndSkip {
  query: string;
  skip: number;
}

export class SegmentsView extends React.Component<SegmentsViewProps, SegmentsViewState> {
  private segmentsQueryManager: QueryManager<QueryAndSkip, any[]>;
  private tableColumnSelectionHandler: TableColumnSelectionHandler;

  constructor(props: SegmentsViewProps, context: any) {
    super(props, context);

    const segmentFilter: Filter[] = [];
    if (props.datasource) segmentFilter.push({ id: 'datasource', value: props.datasource });
    if (props.onlyUnavailable) segmentFilter.push({ id: 'is_available', value: 'false' });

    this.state = {
      segmentsLoading: true,
      segments: null,
      segmentsError: null,
      segmentFilter
    };

    this.segmentsQueryManager = new QueryManager({
      processQuery: async (query: QueryAndSkip) => {
        const results: any[] = (await queryDruidSql({ query: query.query })).slice(query.skip);
        results.forEach(result => {
          try {
            result.payload = JSON.parse(result.payload);
          } catch {
            result.payload = {};
          }
        });
        return results;
      },
      onStateChange: ({ result, loading, error }) => {
        this.setState({
          segments: result,
          segmentsLoading: loading,
          segmentsError: error
        });
      }
    });

    this.tableColumnSelectionHandler = new TableColumnSelectionHandler(
      segmentTableColumnSelection, () => this.setState({})
    );
  }

  componentWillUnmount(): void {
    this.segmentsQueryManager.terminate();
  }

  private fetchData = (state: any, instance: any) => {
    const { page, pageSize, filtered, sorted } = state;
    const totalQuerySize = (page + 1) * pageSize;

    const queryParts = [
      `SELECT "segment_id", "datasource", "start", "end", "size", "version", "partition_num", "num_replicas", "num_rows", "is_published", "is_available", "is_realtime", "payload"`,
      `FROM sys.segments`
    ];

    const whereParts = filtered.map((f: Filter) => {
      if (f.id.startsWith('is_')) {
        if (f.value === 'all') return null;
        return `${JSON.stringify(f.id)} = ${f.value === 'true' ? 1 : 0}`;
      } else {
        return `${JSON.stringify(f.id)} LIKE '%${f.value}%'`;
      }
    }).filter(Boolean);

    if (whereParts.length) {
      queryParts.push('WHERE ' + whereParts.join(' AND '));
    }

    if (sorted.length) {
      queryParts.push('ORDER BY ' + sorted.map((sort: any) => `${JSON.stringify(sort.id)} ${sort.desc ? 'DESC' : 'ASC'}`).join(', '));
    }

    queryParts.push(`LIMIT ${totalQuerySize}`);

    const query = queryParts.join('\n');

    this.segmentsQueryManager.runQuery({
      query,
      skip: totalQuerySize - pageSize
    });
  }

  renderSegmentsTable() {
    const { segments, segmentsLoading, segmentsError, segmentFilter } = this.state;

    return <ReactTable
      data={segments || []}
      pages={10000000} // Dummy, we are hiding the page selector
      loading={segmentsLoading}
      noDataText={!segmentsLoading && segments && !segments.length ? 'No segments' : (segmentsError || '')}
      manual
      filterable
      filtered={segmentFilter}
      defaultSorted={[{id: "start", desc: true}]}
      onFilteredChange={(filtered, column) => {
        this.setState({ segmentFilter: filtered });
      }}
      onFetchData={this.fetchData}
      showPageJump={false}
      ofText=""
      columns={[
        {
          Header: "Segment ID",
          accessor: "segment_id",
          width: 300,
          show: this.tableColumnSelectionHandler.showColumn("Segment ID")
        },
        {
          Header: "Datasource",
          accessor: "datasource",
          Cell: row => {
            const value = row.value;
            return <a onClick={() => { this.setState({ segmentFilter: addFilter(segmentFilter, 'datasource', value) }); }}>{value}</a>;
          },
          show: this.tableColumnSelectionHandler.showColumn("Datasource")
        },
        {
          Header: "Start",
          accessor: "start",
          width: 120,
          defaultSortDesc: true,
          Cell: row => {
            const value = row.value;
            return <a onClick={() => { this.setState({ segmentFilter: addFilter(segmentFilter, 'start', value) }); }}>{value}</a>;
          },
          show: this.tableColumnSelectionHandler.showColumn("Start")
        },
        {
          Header: "End",
          accessor: "end",
          defaultSortDesc: true,
          width: 120,
          Cell: row => {
            const value = row.value;
            return <a onClick={() => { this.setState({ segmentFilter: addFilter(segmentFilter, 'end', value) }); }}>{value}</a>;
          },
          show: this.tableColumnSelectionHandler.showColumn("End")
        },
        {
          Header: "Version",
          accessor: "version",
          defaultSortDesc: true,
          width: 120,
          show: this.tableColumnSelectionHandler.showColumn("Version")
        },
        {
          Header: "Partition",
          accessor: "partition_num",
          width: 60,
          filterable: false,
          show: this.tableColumnSelectionHandler.showColumn("Partition")
        },
        {
          Header: "Size",
          accessor: "size",
          filterable: false,
          defaultSortDesc: true,
          Cell: row => formatBytes(row.value),
          show: this.tableColumnSelectionHandler.showColumn("Size")
        },
        {
          Header: "Num rows",
          accessor: "num_rows",
          filterable: false,
          defaultSortDesc: true,
          Cell: row => formatNumber(row.value),
          show: this.tableColumnSelectionHandler.showColumn("Num rows")
        },
        {
          Header: "Replicas",
          accessor: "num_replicas",
          width: 60,
          filterable: false,
          defaultSortDesc: true,
          show: this.tableColumnSelectionHandler.showColumn("Replicas")
        },
        {
          Header: "Is published",
          id: "is_published",
          accessor: (row) => String(Boolean(row.is_published)),
          Filter: makeBooleanFilter(),
          show: this.tableColumnSelectionHandler.showColumn("Is published")
        },
        {
          Header: "Is realtime",
          id: "is_realtime",
          accessor: (row) => String(Boolean(row.is_realtime)),
          Filter: makeBooleanFilter(),
          show: this.tableColumnSelectionHandler.showColumn("Is realtime")
        },
        {
          Header: "Is available",
          id: "is_available",
          accessor: (row) => String(Boolean(row.is_available)),
          Filter: makeBooleanFilter(),
          show: this.tableColumnSelectionHandler.showColumn("Is available")
        }
      ]}
      defaultPageSize={50}
      className="-striped -highlight"
      SubComponent={rowInfo => {
        const { original } = rowInfo;
        const { payload } = rowInfo.original;
        const dimensions = parseList(payload.dimensions);
        const metrics = parseList(payload.metrics);
        return <div className="segment-detail">
          <H5>Segment ID</H5>
          <p>{original.segment_id}</p>
          <H5>{`Dimensions (${dimensions.length})`}</H5>
          <p>{dimensions.join(', ') || 'No dimension'}</p>
          <H5>{`Metrics (${metrics.length})`}</H5>
          <p>{metrics.join(', ') || 'No metrics'}</p>
        </div>;
      }}
    />;
  }

  render() {
    const { goToSql } = this.props;

    return <div className="segments-view app-view">
      <div className="control-bar">
        <div className="control-label">Segments</div>
        <Button
          iconName={IconNames.REFRESH}
          text="Refresh"
          onClick={() => this.segmentsQueryManager.rerunLastQuery()}
        />
        <Button
          iconName={IconNames.APPLICATION}
          text="Go to SQL"
          onClick={() => goToSql(this.segmentsQueryManager.getLastQuery().query)}
        />
        <TableColumnSelection
          columns={tableColumns}
          onChange={(column) => this.tableColumnSelectionHandler.changeTableColumnSelection(column)}
          tableColumnsHidden={this.tableColumnSelectionHandler.hiddenColumns}
        />
      </div>
      {this.renderSegmentsTable()}
    </div>;
  }
}
