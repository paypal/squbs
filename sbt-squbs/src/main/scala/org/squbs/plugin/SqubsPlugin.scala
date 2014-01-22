/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.plugin

import sbt._
import Keys._
import java.io._
import sbt.KeyRanks._
import java.nio.file.{FileVisitResult, Path, SimpleFileVisitor, Files}
import java.nio.file.attribute.BasicFileAttributes
import sbt.Path
import java.io.File

object SqubsPlugin extends Plugin {

  // We always fork on tests
  fork in ThisBuild := true

  // Also, many of the tests eat up good amount of heap and permgen.
  javaOptions in (Test, run) += "-Xmx2048m -XX:MaxPermSize=512m"

//  override lazy val settings = Seq(commands += myCommand)
  // override lazy val settings = Seq(runtimeCubes)

  val cubeArtifacts = SettingKey[Seq[ModuleID]]("cube-artifacts", "Declares cube artifacts needed at runtime.", APlusSetting)
  val cubeProjects = SettingKey[Seq[Project]]("cube-projects", "Declares cube projects needed at runtime", APlusSetting)
//  val runtimeCubes = settingKey[Seq[ModuleID]]("Declares cubes needed at runtime.")
//
//  val newSetting = settingKey[String]("A new setting.")
//
//  // a group of settings ready to be added to a Project
//  // to automatically add them, do
//  val newSettings = Seq(
//    runtimeCubes := Seq("com.ebay.v3project.v3core" % "GlobalEnvironment" % "1.15.0-b001")
//  )

  val newTask = taskKey[Unit]("A new task.")
  val newSetting = settingKey[String]("A new setting.")

  val createSqubsStructure = taskKey[Unit]("Task to (re)create squbs directory structure.")
//  val runtimeCubes = settingKey[Seq[ModuleID]]("Declares cubes needed at runtime.")

  // a group of settings ready to be added to a Project
  // to automatically add them, do
  override lazy val settings = Seq(
    commands += myCommand,
    newSetting := "test",
    newTask := println(newSetting.value),
    cubeArtifacts := Seq.empty[ModuleID],
    cubeProjects := Seq.empty[Project]
  )



  lazy val myCommand =
    Command.command("hello") { (state: State) =>
      println("Hi!")
//      createSqubsStructure()
      state
    }


  createSqubsStructure := {
    val runDir: File = (baseDirectory in Runtime).value
    val squbsDir = new File(runDir, "squbsRuntime")
    if (squbsDir.exists) squbsDir.deleteTree()
    val cubesDir = new File(squbsDir, "cubes")
    if (!cubesDir.mkdirs) println(s"Cannot create directory ${cubesDir.getAbsolutePath}")
    val cubes: Seq[Project] = (cubeProjects in Runtime).value
    cubes foreach { project =>
      // settings in test
      val cubeName: String = project.id
      println(s"Cube name is $cubeName")
      val cubeFile = new File(cubesDir, cubeName)
      val classPath1: Classpath = (dependencyClasspath in Runtime).value
      val classPath2: Seq[File] = classPath1.files

      val ccp = Def.settingDyn {
        dependencyClasspath in(project, Runtime)
      }
      val lcp = taskKey[Seq[File]]("Loosely coupled classpath")
      lcp := ccp.value.files
      val writeCubeFile = taskKey[Unit]("Create the cube file")
      writeCubeFile := {(lcp.value map { _.getAbsolutePath + "\n"} mkString "") > cubeFile}
    }
  }

  implicit class FileExtensions(val f: File) extends AnyVal {

    def deleteTree() {
      val dir = f.toPath
      try {
        Files.walkFileTree(dir, new SimpleFileVisitor[Path]() {

          import FileVisitResult._

          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            println(s"Deleting file: $file")
            Files.delete(file)
            CONTINUE
          }

          override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
            println(s"Deleting dir: $dir")
            if (exc == null) {
              Files.delete(dir)
              CONTINUE
            } else throw exc
          }

        })
      } catch {
        case e: IOException => e.printStackTrace()
      }
    }
  }

  implicit class StringOutput(val content: String) extends AnyVal {

    // Writes this string to a file
    def >(file: File) {
      val writer = new PrintWriter(file, "UTF-8")
      writer.append(content)
      if (!(content endsWith "\n"))
        writer.append('\n')
      writer.close()
    }
  }
}
