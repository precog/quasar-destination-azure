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

import slamdata.Predef._

import quasar.EffectfulQSpec
import quasar.api.Column
import quasar.api.resource.{ResourceName, ResourcePath}
import quasar.blobstore.paths.{BlobPath, PathElem}
import quasar.blobstore.services.PutService
import quasar.connector.destination.ResultSink

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import cats.effect.{IO, Timer}
import cats.implicits._
import fs2.{Stream, text}

object AzureDestinationSpec extends EffectfulQSpec[IO] {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  "adds csv extension to existing one" >>* {
    val expectedKey = BlobPath(List(PathElem("foo"), PathElem("bar.whatever.csv")))
    for {
      (put, ref) <- MockPut.empty
      testPath = ResourcePath.root() / ResourceName("foo") / ResourceName("bar.whatever")
      bytes = Stream("foobar").through(text.utf8Encode)
      _ <- run(put, testPath, bytes)
      keys <- ref.get.map(_.keys)
    } yield {
      keys must contain(exactly(expectedKey))
    }
  }

  "adds csv extension if there is no extension" >>* {
    val expectedKey = BlobPath(List(PathElem("foo"), PathElem("bar.csv")))
    for {
      (put, ref) <- MockPut.empty
      testPath = ResourcePath.root() / ResourceName("foo") / ResourceName("bar")
      bytes = Stream("foobar").through(text.utf8Encode)
      _ <- run(put, testPath, bytes)
      keys <- ref.get.map(_.keys)
    } yield {
      keys must contain(exactly(expectedKey))
    }
  }

  "doesn't add csv extension if already has" >>* {
    val expectedKey = BlobPath(List(PathElem("foo"), PathElem("bar.csv")))
    for {
      (put, ref) <- MockPut.empty
      testPath = ResourcePath.root() / ResourceName("foo") / ResourceName("bar.csv")
      bytes = Stream("foobar").through(text.utf8Encode)
      _ <- run(put, testPath, bytes)
      keys <- ref.get.map(_.keys)
    } yield {
      keys must contain(exactly(expectedKey))
    }
  }

  "puts results" >>* {
    for {
      (put, ref) <- MockPut.empty
      testPath = ResourcePath.root() / ResourceName("foo.csv")
      bytes = Stream("push this").through(text.utf8Encode)
      _ <- run(put, testPath, bytes)
      currentStatus <- ref.get
    } yield {
      currentStatus.get(BlobPath(List(PathElem("foo.csv")))) must beSome("push this")
    }
  }

  private def run(put: PutService[IO], path: ResourcePath, bytes: Stream[IO, Byte]): IO[Unit] =
    findCsvSink(new AzureDestination(put).sinks).fold(
      IO.raiseError[Unit](new Exception("Could not find CSV sink in AzureDestination"))
    )(_.consume(path, NonEmptyList.one(Column("test", ())))._2(bytes).compile.drain)

  private def findCsvSink(sinks: NonEmptyList[ResultSink[IO, Unit]]): Option[ResultSink.CreateSink[IO, Unit, Byte]] =
    sinks collectFirstSome {
      case csvSink @ ResultSink.CreateSink(_) =>
        csvSink.asInstanceOf[ResultSink.CreateSink[IO, Unit, Byte]].some
      case _ =>
        None
    }
}

object MockPut {
  private def fromRef(status: Ref[IO, Map[BlobPath, String]]): PutService[IO] = Kleisli { case (key, bytes) =>
    for {
      data <- bytes.through(text.utf8Decode).foldMonoid.compile.lastOrError
      _ <- status.update(_ + (key -> data))
    } yield data.length
  }
  def empty: IO[(PutService[IO], Ref[IO, Map[BlobPath, String]])] =
    Ref.of[IO, Map[BlobPath, String]](Map.empty[BlobPath, String])
      .map(ref => (fromRef(ref), ref))
}
