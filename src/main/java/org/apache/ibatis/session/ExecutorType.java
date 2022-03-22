/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.session;

/**
 * 执行器类型.
 *
 * @author Clinton Begin
 */
public enum ExecutorType {
  // SIMPLE: 普通执行器. 这个执行器类型不做特殊的事情,它会为每个语句的执行都创建一个新的预处理语句.
  // REUSE: 会复用预处理语句的执行器(PreparedStatement).
  // BATCH: 执行器不仅会复用预处理语句,还会执行批量更新. 如果SELECT在它们中间执行还会标定它们是必须的,来保证一个简单并易于理解的行为.
  SIMPLE, REUSE, BATCH
}
