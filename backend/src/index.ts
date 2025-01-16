import "@aws-sdk/crc64-nvme-crt";

import { join, resolve } from "path";
import { createWriteStream, existsSync, mkdirSync } from "fs";

import Koa from "koa";
import { S3Client, GetObjectCommand } from "@aws-sdk/client-s3";

import { ENV } from "./env.config.ts";

const server = new Koa();

server.listen(3000, async () => {
  console.log("Server started on port 3000");

  const client = new S3Client({
    region: ENV.AWS.REGION,
    credentials: {
      accessKeyId: ENV.AWS.ACCESS_KEY_ID,
      secretAccessKey: ENV.AWS.SECRET_ACCESS_KEY,
    },
    runtime: "node",
  });

  const bookFileName = "meditations.pdf";
  const downloadPath = resolve(import.meta.dirname, "downloads");
  if (!existsSync(downloadPath)) {
    mkdirSync(downloadPath);
  }

  try {
    // Create the GetObjectCommand
    const command = new GetObjectCommand({
      Bucket: ENV.AWS.BUCKET_NAME,
      Key: bookFileName,
    });

    // Send the command
    const response = await client.send(command);

    if (!response.Body) {
      throw new Error("No file body found in the S3 response.");
    }

    // Create a write stream to save the file locally
    const filePath = join(downloadPath, bookFileName);
    const writeStream = createWriteStream(filePath);

    // Pipe the response body to the file
    (response.Body as NodeJS.ReadableStream).pipe(writeStream);

    writeStream.on("finish", () => {
      console.log(`File downloaded successfully to ${filePath}`);
    });

    writeStream.on("error", (err) => {
      console.error("Error writing the file:", err);
    });
  } catch (error) {
    console.error("Error downloading the file:", error);
  }
});
