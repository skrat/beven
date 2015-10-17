package main

import (
	"os"
	"fmt"
	"flag"
	"bytes"
	"net/http"
	"io/ioutil"
	"text/template"
	"github.com/satori/go.uuid"
	"github.com/go-martini/martini"
	"github.com/garyburd/redigo/redis"
)

var (
	redisAddress   = flag.String("redis-address", ":6379", "Address to the Redis server")
	maxConnections = flag.Int("max-connections", 10, "Max connections to Redis")
	database       = flag.Int("database", 1, "Redis database index")
)

func Save(pool *redis.Pool, req *http.Request) (int, string) {
	c := pool.Get()
	defer c.Close()

	id := uuid.NewV4()
	for i := 0; i < 10; i++ {
		exists, err := c.Do("EXISTS", id.Bytes())
		if err != nil {
			return 500, fmt.Sprint(err)
		}
		if exists == int64(0) {
			break
		}
		id = uuid.NewV4()
	}

	body, err := ioutil.ReadAll(req.Body)
	if err != nil {
		return 500, fmt.Sprint(err)
	}
	if len(body) > 10000 {
		return 422, "Body too large"
	}
	_, err = c.Do("SET", id.Bytes(), body)
	if err != nil {
		return 500, fmt.Sprint(err)
	}

	return 200, id.String()
}

func Load(pool *redis.Pool, t *template.Template, params martini.Params) (int, string) {
	c := pool.Get()
	defer c.Close()

	key := params["key"]
	id, err := uuid.FromString(key)
	if err != nil {
		return 500, fmt.Sprint(err)
	}

	state, err := redis.String(c.Do("GET", id.Bytes()))
	if err != nil {
		return 500, fmt.Sprint(err)
	}

	var doc bytes.Buffer
	t.Execute(&doc, state)

	return 200, doc.String()
}

func main() {
	flag.Parse()
  m := martini.Classic()

	var pool = redis.NewPool(func() (redis.Conn, error) {
		c, err := redis.Dial("tcp", *redisAddress)
		if err != nil {
			return nil, err
		}
		_, err = c.Do("SELECT", *database)
		if err != nil {
			return nil, err
		}
		return c, err
	}, *maxConnections)
	defer pool.Close()
	m.Map(pool)

	if os.Getenv("DEV") == "1" {
		m.Use(martini.Static("resources/public"))
	}

	f, _ := ioutil.ReadFile("resources/public/index.html")
	t, _ := template.New("index").Parse(string(f))
	m.Map(t)

  m.Post("/save", Save)
  m.Get("/:key",  Load)
  m.Run()
}
