/*
 * Copyright 2018-2020 Radicalbit S.r.l.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.radicalbit.nsdb.cluster.logic

import akka.cluster.metrics.NodeMetrics

/**
  * Contains the method to select write nodes in a cluster.
  */
trait WriteNodesSelectionLogic {

  /**
    * Applies the selection logic for the given set of metrics.
    * @param nodeMetrics the metrics collected for the nodes.
    * @param replicationFactor number of replicas configured.
    * @return the selected node names sequence.
    */
  def selectWriteNodes(nodeMetrics: Set[NodeMetrics], replicationFactor: Int): Seq[String]
}