import { join, resolve } from "path";
import Koa from "koa";
import Router from "koa-router";
import { BooksService } from "./services/books.service.ts";

const router = new Router();
const server = new Koa();

router.get("/get-book-content", async (ctx, next) => {
  try {
    const fileName = ctx.request.query.fileName as string;
    if (!fileName) {
      ctx.status = 400;
      ctx.body = new Error("File name is required.");
      return;
    }

    const booksService = new BooksService();
    const fileStream = await booksService.getBookFileStream(fileName);

    // Set response headers for a PDF
    ctx.set("Content-Type", "application/pdf");
    ctx.set("Content-Disposition", `attachment; filename=${fileName}`);

    // Pipe the file stream directly to the client
    ctx.body = fileStream;
  } catch (error) {
    console.error("Error fetching file:", error);
    ctx.status = 500;
    ctx.body = "Error fetching file.";
  }
});

server.use(router.routes());

server.listen(3000, async () => {
  console.log("Server started on port 3000");
});
