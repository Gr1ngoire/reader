import Koa from "koa";

const server = new Koa();

server.listen(3000, async () => {
  console.log("Server started on port 3000");
});
