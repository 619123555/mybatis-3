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
 * @author Eduardo Macarron
 */
public enum LocalCacheScope {
  // SESSION: 以会话为单位进行缓存.
  // STATEMENT: 得到查询结果后不进行缓存(每个sql都会在拿到sql结果后,添加到缓存集合中,再清空缓存列表,所以当并发高时,也会导致脏读.
  SESSION,STATEMENT
}
