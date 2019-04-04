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

import { Button, Classes, Dialog, FormGroup, InputGroup, TextArea } from "@blueprintjs/core";
import * as React from "react";

import "./query-plan-dialog.scss";

export interface QueryPlanDialogProps extends React.Props<any> {
  explainResult: any;
  explainError: Error | null;
  onClose: () => void;
}

export interface QueryPlanDialogState {

}

export class QueryPlanDialog extends React.Component<QueryPlanDialogProps, QueryPlanDialogState> {

  constructor(props: QueryPlanDialogProps) {
    super(props);
    this.state = {};
  }

  render() {
    const { explainResult, explainError, onClose } = this.props;

    let content: JSX.Element;

    if (explainError) {
      content = <div>{explainError.message}</div>;
    } else if (explainResult == null) {
      content = <div/>;
    } else if (explainResult.query) {

      let signature: JSX.Element | null = null;
      if (explainResult.signature) {
        signature = <FormGroup
          label={"Signature"}
        >
          <InputGroup defaultValue={explainResult.signature} readOnly/>
        </FormGroup>;
      }

      content = <div className={"one-query"}>
        <FormGroup
          label={"Query"}
        >
          <TextArea
            readOnly
            value={JSON.stringify(explainResult.query[0], undefined, 2)}
          />
        </FormGroup>
        {signature}
      </div>;
    } else if (explainResult.mainQuery && explainResult.subQueryRight) {

      let mainSignature: JSX.Element | null = null;
      let subSignature: JSX.Element | null = null;
      if (explainResult.mainQuery.signature) {
        mainSignature = <FormGroup
          label={"Signature"}
        >
          <InputGroup defaultValue={explainResult.mainQuery.signature} readOnly/>
        </FormGroup>;
      }
      if (explainResult.subQueryRight.signature) {
        subSignature = <FormGroup
          label={"Signature"}
        >
          <InputGroup defaultValue={explainResult.subQueryRight.signature} readOnly/>
        </FormGroup>;
      }

      content = <div className={"two-queries"}>
        <FormGroup
          label={"Main query"}
        >
          <TextArea
            readOnly
            value={JSON.stringify(explainResult.mainQuery.query, undefined, 2)}
          />
        </FormGroup>
        {mainSignature}
        <FormGroup
          label={"Sub query"}
        >
          <TextArea
            readOnly
            value={JSON.stringify(explainResult.subQueryRight.query, undefined, 2)}
          />
        </FormGroup>
        {subSignature}
      </div>;
    } else {
      content = <div>{explainResult}</div>;
    }

    return <Dialog
      className={'query-plan-dialog'}
      isOpen
      onClose={onClose}
      title={"Query plan"}
    >
      <div className={Classes.DIALOG_BODY}>
        {content}
      </div>
      <div className={Classes.DIALOG_FOOTER}>
        <div className={Classes.DIALOG_FOOTER_ACTIONS}>
          <Button
            text="Close"
            onClick={onClose}
          />
        </div>
      </div>
    </Dialog>;
  }
}
