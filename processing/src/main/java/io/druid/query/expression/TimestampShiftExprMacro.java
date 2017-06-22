/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.expression;

import io.druid.java.util.common.IAE;
import io.druid.java.util.common.granularity.PeriodGranularity;
import io.druid.math.expr.Expr;
import io.druid.math.expr.ExprEval;
import io.druid.math.expr.ExprMacroTable;
import org.joda.time.Chronology;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;

import javax.annotation.Nonnull;
import java.util.List;

public class TimestampShiftExprMacro implements ExprMacroTable.ExprMacro
{
  @Override
  public String name()
  {
    return "timestamp_shift";
  }

  @Override
  public Expr apply(final List<Expr> args)
  {
    if (args.size() < 3 || args.size() > 4) {
      throw new IAE("'%s' must have 3 to 4 arguments", name());
    }

    final Expr arg = args.get(0);

    if (args.get(1).isLiteral()) {
      final PeriodGranularity granularity = getGranularity(args, ExprUtils.nilBindings());
      final Period period = granularity.getPeriod();
      final Chronology chronology = ISOChronology.getInstance(granularity.getTimeZone());
      final int step = getStep(args, ExprUtils.nilBindings());

      class TimestampShiftExpr implements Expr
      {
        @Nonnull
        @Override
        public ExprEval eval(final ObjectBinding bindings)
        {
          return ExprEval.of(chronology.add(period, arg.eval(bindings).asLong(), step));
        }

        @Override
        public void visit(final Visitor visitor)
        {
          arg.visit(visitor);
          visitor.visit(this);
        }
      }
      return new TimestampShiftExpr();
    } else {
      // Use dynamic impl if any args are non-literal. Don't bother optimizing for the case where period is
      // literal but step isn't.
      class TimestampShiftDynamicExpr implements Expr
      {
        @Nonnull
        @Override
        public ExprEval eval(final ObjectBinding bindings)
        {
          final PeriodGranularity granularity = getGranularity(args, bindings);
          final Period period = granularity.getPeriod();
          final Chronology chronology = ISOChronology.getInstance(granularity.getTimeZone());
          final int step = getStep(args, bindings);
          return ExprEval.of(chronology.add(period, arg.eval(bindings).asLong(), step));
        }

        @Override
        public void visit(final Visitor visitor)
        {
          arg.visit(visitor);
          visitor.visit(this);
        }
      }
      return new TimestampShiftDynamicExpr();
    }
  }

  private static PeriodGranularity getGranularity(final List<Expr> args, final Expr.ObjectBinding bindings)
  {
    return ExprUtils.toPeriodGranularity(
        args.get(1),
        null,
        args.size() > 3 ? args.get(3) : null,
        bindings
    );
  }

  private static int getStep(final List<Expr> args, final Expr.ObjectBinding bindings)
  {
    return args.get(2).eval(bindings).asInt();
  }
}
