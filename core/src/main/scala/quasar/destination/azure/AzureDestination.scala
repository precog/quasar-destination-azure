/*
 * Copyright 2020 Precog Data
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

package quasar.destination.azure

import scala._
import java.lang.String

import quasar.api.destination.DestinationType
import quasar.api.resource.ResourcePath
import quasar.blobstore.azure.{AzurePutService, Expires}
import quasar.blobstore.paths.{BlobPath, PathElem}
import quasar.blobstore.services.PutService
import quasar.connector.destination.{ResultSink, UntypedDestination}
import quasar.connector.render.RenderConfig

import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._
import com.azure.storage.blob.BlobContainerAsyncClient
import fs2.{Pipe, Stream}

final case class AzureDestination[F[_]: ConcurrentEffect: ContextShift: Timer] private[azure] (
    put: PutService[F])
    extends UntypedDestination[F] {

  import AzureDestination._

  def destinationType: DestinationType = DestinationType("azure-dest", 1L)

  def sinks: NonEmptyList[ResultSink[F, Unit]] = NonEmptyList.one(csvSink)

  private def csvSink = ResultSink.create[F, Unit, Byte] { (path, _) =>
    val pipe: Pipe[F, Byte, Unit] = bytes => Stream.eval {
      put((toBlobPath(path), bytes)).void
    }

    (RenderConfig.Csv(), pipe)
  }

  private def toBlobPath(path: ResourcePath): BlobPath = {
    val parts = toPath(path)
    val suffixized = parts.reverse match {
      case hd :: tail => modifySuffix(hd) :: tail
      case other => other
    }
    BlobPath(suffixized.reverse.map(PathElem(_)))
  }

  private def modifySuffix(inp: String): String = {
    val idx = inp.lastIndexOf(".")
    lazy val added = inp + "." + MandatoryExtension
    if (idx === -1) added
    else {
      val ext = inp.substring(idx + 1)
      if (ext === MandatoryExtension) inp
      else added
    }
  }

  private def toPath(path: ResourcePath): List[String] =
    ResourcePath.resourceNamesIso.get(path).map(n => n.value).toList
}

object AzureDestination {
  val MandatoryExtension = "csv"

  def apply[F[_]: ConcurrentEffect: ContextShift: Timer](
      refContainerClient: Ref[F, Expires[BlobContainerAsyncClient]],
      refresh: F[Unit]): UntypedDestination[F] = {
    val put: PutService[F] = Kleisli { req =>
      for {
        _ <- refresh
        containerClient <- refContainerClient.get
        unwrappedPut: PutService[F] = AzurePutService.mk[F](containerClient.value)
        result <- unwrappedPut.run(req)
      } yield result
    }
    new AzureDestination(put)
  }
}
