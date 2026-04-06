import express from 'express' 
import supabase from './config/supabase.js' 
import routers from './routers/index.js'

let app = express();

app.use(express.json()); 


app.use('/auth', routers.authRouter);
app.use('/address', routers.addressRouter);
app.use('/api', routers.apiRouter);

export default app;

