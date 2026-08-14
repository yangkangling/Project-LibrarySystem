# 图书馆借阅系统前端

这个目录是从原来的 `src/main/resources/static/index.html` 迁出的 Vue3 前端工程。

## 技术栈

- Vue3
- Vite
- Element Plus
- Axios

## 开发启动

先启动 Spring Boot 后端，默认端口是 `8080`。

然后在本目录安装依赖并启动前端：

```bash
npm install
npm run dev
```

访问：

```text
http://localhost:5173/
```

Vite 已在 `vite.config.js` 中配置代理，请求 `/auth`、`/books`、`/borrow`、`/self` 等接口时会转发到 `http://localhost:8080`。

## 构建到 Spring Boot 静态目录

```bash
npm run build:spring
```

构建产物会输出到：

```text
../src/main/resources/static
```

之后只启动 Spring Boot，也可以通过：

```text
http://localhost:8080/
```

访问 Vue3 版本页面。
