import { Readable } from "stream";

import "@aws-sdk/crc64-nvme-crt";
import { S3Client, GetObjectCommand } from "@aws-sdk/client-s3";

import { ENV } from "../config/env.config.ts";

class BooksService {
  private client: S3Client;

  constructor() {
    this.client = new S3Client({
      region: ENV.AWS.REGION,
      credentials: {
        accessKeyId: ENV.AWS.ACCESS_KEY_ID,
        secretAccessKey: ENV.AWS.SECRET_ACCESS_KEY,
      },
      runtime: "node",
    });
  }

  public async getBookFileStream(bookFileName: string): Promise<Readable> {
    const command = new GetObjectCommand({
      Bucket: ENV.AWS.BUCKET_NAME,
      Key: bookFileName,
    });

    const { Body } = await this.client.send(command);
    if (!Body) {
      throw new Error("No file body found in the S3 response.");
    }

    if (!(Body instanceof Readable)) {
      throw new Error("File stream could not be retrieved.");
    }

    return Body;
  }
}

export { BooksService };
