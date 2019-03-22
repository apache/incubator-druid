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

import {
  Button,
  Classes,
  Dialog,
  IDialogProps,
  InputGroup,
  Intent
} from "@blueprintjs/core";
import * as React from 'react';

import { FormGroup, IconNames } from '../components/filler';
import { localStorageGet, localStorageSet } from "../utils";

import { HistoryDialog } from "./history-dialog";

const druidEditingAuthor = "DRUID_EDITING_AUTHOR";

export interface SnitchDialogProps extends IDialogProps {
  onSave: (author: string, comment: string) => void;
  saveDisabled?: boolean;
  onReset?: () => void;
  historyRecords?: any[];
}

export interface SnitchDialogState {
  author: string;
  comment: string;

  showFinalStep?: boolean;
  saveDisabled?: boolean;

  showHistory?: boolean;
}

export class SnitchDialog extends React.Component<SnitchDialogProps, SnitchDialogState> {
  constructor(props: SnitchDialogProps) {
    super(props);

    this.state = {
      comment: "",
      author: "",
      saveDisabled: true
    };
  }

  componentDidMount(): void {
    this.getDefaultAuthor();
  }

  save = () => {
    const { onSave, onClose } = this.props;
    const { author, comment } = this.state;

    onSave(author, comment);
    if (onClose) onClose();
  }

  getDefaultAuthor() {
    const author: string | null = localStorageGet(druidEditingAuthor);
    if (author) {
      this.setState({
        author
      });
    }
  }

  changeAuthor(newAuthor: string)  {
    const { author, comment } = this.state;

    this.setState({
      author: newAuthor,
      saveDisabled: !newAuthor || !comment
    });
    localStorageSet(druidEditingAuthor, newAuthor);
  }

  changeComment(newComment: string)  {
    const { author, comment } = this.state;

    this.setState({
      comment: newComment,
      saveDisabled: !author || !newComment
    });
  }

  reset = () => {
    const { onReset } = this.props;

    if (onReset) onReset();
  }

  back = () => {
    this.setState({
      showFinalStep: false,
      showHistory: false
    });
  }

  goToFinalStep = () => {
    this.setState({
      showFinalStep: true
    });
  }

  goToHistory = () => {
    this.setState({
      showHistory: true
    });
  }

  renderFinalStep() {
    const { onClose, children } = this.props;
    const { saveDisabled, author, comment } = this.state;

    return <Dialog {...this.props}>
      <div className={`dialog-body ${Classes.DIALOG_BODY}`}>
        <FormGroup label={"Who is making this change?"}>
          <InputGroup value={author} onChange={(e: any) => this.changeAuthor(e.target.value)}/>
        </FormGroup>
        <FormGroup label={"Why are you making this change?"} className={"comment"}>
          <InputGroup
            className="pt-large"
            value={comment}
            placeholder={"Enter description here"}
            onChange={(e: any) => this.changeComment(e.target.value)}
          />
        </FormGroup>
      </div>

      <div className={Classes.DIALOG_FOOTER}>
        {this.renderActions(saveDisabled)}
      </div>
    </Dialog>;
  }

  renderHistoryButton() {
    const { historyRecords } = this.props;
    const { showFinalStep } = this.state;
    if (showFinalStep || historyRecords === undefined) return null;
    return <Button style={{float: "left"}} className={"pt-minimal"} text="Audit history" onClick={this.goToHistory}/>;
  }

  renderHistoryDialog() {
    const { historyRecords } = this.props;
    return <HistoryDialog
      {...this.props}
      historyRecords={historyRecords}
    >
      <div className={Classes.DIALOG_FOOTER_ACTIONS}>
        <Button onClick={this.back} iconName={IconNames.ARROW_LEFT}>Back</Button>
      </div>
    </HistoryDialog>;
  }

  renderActions(saveDisabled?: boolean) {
    const { onReset, historyRecords } = this.props;
    const { showFinalStep } = this.state;

    return <div className={Classes.DIALOG_FOOTER_ACTIONS}>
      { showFinalStep
        ? <Button onClick={this.back} iconName={IconNames.ARROW_LEFT}>Back</Button>
        : onReset ? <Button onClick={this.reset} intent={"none" as any}>Reset</Button> : null
      }

      { showFinalStep
        ? <Button disabled={saveDisabled} text="Save" onClick={this.save} intent={Intent.PRIMARY as any} rightIconName={IconNames.TICK}/>
        : <Button disabled={saveDisabled} text="Next" onClick={this.goToFinalStep} intent={Intent.PRIMARY as any} rightIconName={IconNames.ARROW_RIGHT}/>
      }
    </div>;
  }

  render() {
    const { onClose, className, children, saveDisabled } = this.props;
    const { showFinalStep, showHistory } = this.state;

    if (showFinalStep) return this.renderFinalStep();
    if (showHistory) return this.renderHistoryDialog();

    return <Dialog isOpen inline {...this.props}>
      <div className={Classes.DIALOG_BODY}>
        {children}
      </div>

      <div className={Classes.DIALOG_FOOTER}>
        {this.renderHistoryButton()}
        {this.renderActions(saveDisabled)}
      </div>
    </Dialog>;
  }
}
