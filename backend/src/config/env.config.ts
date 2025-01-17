const ENV = {
  AWS: {
    ACCESS_KEY_ID: process.env.AWS_ACCESS_KEY_ID as string,
    SECRET_ACCESS_KEY: process.env.AWS_SECRET_ACCESS_KEY as string,
    REGION: process.env.AWS_REGION as string,
    BUCKET_NAME: process.env.AWS_BOOKS_BUCKET_NAME as string,
  },
};

export { ENV };
