/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.utils.conf

import java.io.File
import scala.xml.XML
import java.io.FileNotFoundException

import com.typesafe.scalalogging.LazyLogging

object ConfigLoader extends LazyLogging {
  val GEOMESA_CONFIG_FILE_PROP = "geomesa.config.file"
  val GEOMESA_CONFIG_FILE_NAME = "geomesa-site.xml"
  val GEOMESA_EMBEDDED_CONFIG_FILE_PATH = "/org/locationtech/geomesa/geomesa-site.xml.template"

  val init: String = "Loaded"

  val sysConfig = Option(System.getProperty(GEOMESA_CONFIG_FILE_PROP)).getOrElse("")
  val classConfig = Option(getClass.getClassLoader.getResource(GEOMESA_CONFIG_FILE_NAME)).getOrElse("")

  // Load program defaults first
  logger.info("Loading Config Defaults.")
  loadConfig(XML.load(getClass.getResourceAsStream(GEOMESA_EMBEDDED_CONFIG_FILE_PATH)))

  // Overwrite with any provided config file
  if (sysConfig.nonEmpty) {
    loadConfig(sysConfig)
  } else if (classConfig.toString.nonEmpty) {
    loadConfig(getClass.getClassLoader.getResource(GEOMESA_CONFIG_FILE_NAME).getFile)
  }

  def loadConfig(path: String): Unit = {
    try {
      val xml = XML.loadFile(path)
      logger.info("Using GeoMesa config file found at: " + path)
      loadConfig(xml)
    } catch {
      case fnfe: FileNotFoundException => logger.warn("Unable to find GeoMesa config file at: " + path, fnfe)
      case _: Throwable => logger.warn("Error reading config file: " + path, _: Throwable)
    }
  }

  def loadConfig(xml: scala.xml.Elem): Unit = {
    val properties = xml \\ "configuration" \\ "property"
    properties.foreach { prop =>
      try { // Use try/catch here so if we fail on a property the rest can still load
        val key = (prop \ "name").text
        val value = (prop \ "value").text
        val isfinal: Boolean = (prop \ "final").text.toString.toBoolean
        // Don't overwrite properties, this gives commandline params preference
        if (Option(System.getProperty(key)).getOrElse("").isEmpty && value.nonEmpty || isfinal) {
          System.setProperty(key, value)
          logger.debug("Set System Property: " + key + ":" + value)
        }
      } catch {
        // This catches failure to convert isfinal to boolean and key = ""
        case iae: IllegalArgumentException => logger.warn("Unable to set system property, this is most likely "
          + s"due to a malformed configuration file (${GEOMESA_CONFIG_FILE_NAME}). Perhaps you wouldn't have this "
          + "problem if you typed with more than two fingers.\n" + "Property:\n" + prop, iae)
        case _: Throwable => logger.warn("Error setting system property.", _: Throwable)
      }
    }
    logger.debug("Config loaded.")
  }
}
