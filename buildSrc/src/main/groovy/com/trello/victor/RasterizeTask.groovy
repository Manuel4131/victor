/*
 * Copyright 2015 Trello, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trello.victor
import org.apache.batik.transcoder.Transcoder
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.apache.batik.transcoder.image.PNGTranscoder
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails

class RasterizeTask extends DefaultTask {

    @InputFiles
    FileCollection sources

    @OutputDirectory
    File outputDir

    @Input
    List<Density> includeDensities

    @Input
    int baseDpi

    // TODO: Make this an incremental build
    @TaskAction
    def rasterize(IncrementalTaskInputs inputs) {
        // If the whole thing isn't incremental, delete the build folder (if it exists)
        if (!inputs.isIncremental() && outputDir.exists()) {
            logger.debug("SVG rasterization is not incremental; deleting build folder and starting fresh!")
            outputDir.delete()
        }

        List<File> svgFiles = []
        inputs.outOfDate { InputFileDetails change ->
            logger.debug("$change.file.name out of date; converting")
            svgFiles.add change.file
        }

        includeDensities.each { Density density ->
            File resDir = getResourceDir(density)
            resDir.mkdirs()

            Transcoder transcoder = new PNGTranscoder();
            float pixelUnitToMillimeter = (2.54f / (baseDpi * density.multiplier)) * 10
            transcoder.addTranscodingHint(ImageTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER,
                    new Float(pixelUnitToMillimeter));

            svgFiles.each { File svgFile ->
                String svgURI = svgFile.toURI().toString();
                TranscoderInput input = new TranscoderInput(svgURI);

                File destination = new File(resDir, getDestinationFile(svgFile.name))
                OutputStream outStream = new FileOutputStream(destination)
                TranscoderOutput output = new TranscoderOutput(outStream);

                transcoder.transcode(input, output);

                outStream.flush();
                outStream.close();

                logger.info("Converted $svgFile to $destination")
            }
        }

        inputs.removed { change ->
            logger.debug("$change.file.name was removed; removing it from generated folder")

            includeDensities.each { Density density ->
                File resDir = getResourceDir(density)
                File file = new File(resDir, getDestinationFile(change.file.name))
                file.delete()
            }
        }
    }

    File getResourceDir(Density density) {
        return new File(outputDir, "/drawable-${density.name().toLowerCase()}")
    }

    String getDestinationFile(String name) {
        int suffixStart = name.lastIndexOf '.'
        return suffixStart == -1 ? name : "${name.substring(0, suffixStart)}.png"
    }
}