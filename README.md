# Agro AI - Spring Boot + React

Апликација базирана на спецификацијата за систем "ВИ во земјоделство и шумарство".

## Што е имплементирано

- Регистрација и најава (JWT токен).
- CRUD за парцели (додади, листај, избриши).
- Интеграција со `Open-Meteo Forecast API`.
- Генерирање препорака по парцела (култура, ризик, очекуван принос, објаснување).
- Историја на анализи.
- React dashboard UI со модерен layout, подготвен за доработка според Figma.

## Backend (Spring Boot)

1. Отвори терминал во `backend`
2. Старт:
   - `mvn spring-boot:run`

API:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/parcels`
- `POST /api/parcels`
- `DELETE /api/parcels/{id}`
- `POST /api/recommendations/generate/{parcelId}`
- `GET /api/recommendations/history`

## Frontend (React + Vite)

1. Отвори терминал во `frontend`
2. Инсталирај пакети: `npm install`
3. Старт: `npm run dev`
4. Отвори `http://localhost:5173`

## Figma интеграција (дизајн)

Во спецификацијата е даден линк:
- [Open-Meteo Web Application Design](https://www.figma.com/make/fuZDvaidr0fj24yBjeCAIb/Open-Meteo-Web-Application-Design?p=f)

Тековниот UI е поставен како функционален baseline. Следен чекор е:
- прецизно пресликување на spacing, typography и color tokens од Figma,
- додавање dedicated страници (reports, import/export, admin),
- компонентен систем (`Button`, `Card`, `Input`, `Table`) 1:1 според Figma.
