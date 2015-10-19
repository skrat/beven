package main

import (
	"os"
	"fmt"
	"flag"
	"bytes"
	"net/http"
	"io/ioutil"
	"crypto/sha1"
	"crypto/rand"
	"text/template"
	"github.com/go-martini/martini"
	"github.com/garyburd/redigo/redis"
)

var (
	redisAddress   = flag.String("redis-address", ":6379", "Address to the Redis server")
	maxConnections = flag.Int("max-connections", 10, "Max connections to Redis")
	database       = flag.Int("database", 1, "Redis database index")
	secret         = flag.String("secret", "If you can dream it, you can do it", "For edit URLs")
	bind           = flag.String("bind", "127.0.0.1:3342", "Listen on this address")
)

func obscurity(id string) string {
	hash := sha1.New()
  hash.Write([]byte(*secret))
  hash.Write([]byte(id))
  sum := hash.Sum(nil)
	return fmt.Sprintf("%s/%s", id, fmt.Sprintf("%x", sum)[:6])
}

func security(id string, sums string) bool {
	hash := sha1.New()
  hash.Write([]byte(*secret))
  hash.Write([]byte(id))
  sum := hash.Sum(nil)
	return fmt.Sprintf("%x", sum)[:6] == sums
}

func genID() string {
	b := make([]byte, 3)
	rand.Read(b)
	return fmt.Sprintf("%x", b)
}

func Save(pool *redis.Pool, req *http.Request, params martini.Params) (int, string) {
	c := pool.Get()
	defer c.Close()

  id := genID()
	param_id, ok := params["id"]

	if ok {
		if security(param_id, params["sum"]) {
			id = param_id
		} else {
			return 401, "Not your bill"
		}
	} else {
		for i := 0; i < 10; i++ {
			exists, err := c.Do("EXISTS", id)
			if err != nil {
				return 500, fmt.Sprint(err)
			}
			if exists == int64(0) {
				break
			}
			id = genID()
		}
	}

	body, err := ioutil.ReadAll(req.Body)
	if err != nil {
		return 500, fmt.Sprint(err)
	}
	if len(body) > 10000 {
		return 422, "Body too large"
	}
	_, err = c.Do("SET", id, body)
	if err != nil {
		return 500, fmt.Sprint(err)
	}

	return 200, obscurity(id)
}

func Load(pool *redis.Pool, t *template.Template, params martini.Params) (int, string) {
	c := pool.Get()
	defer c.Close()

	var doc bytes.Buffer
	key, ok := params["id"]

	if ok {
		state, err := redis.String(c.Do("GET", key))
		if err != nil {
			return 500, fmt.Sprint(err)
		}
		t.Execute(&doc, state)
	} else {
		t.Execute(&doc, "")
	}

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
  m.Post("/:id/:sum", Save)

  m.Get("/", Load)
  m.Get("/:id", Load)
  m.Get("/:id/:sum", Load)

	if os.Getenv("DEV") == "1" {
		m.Run()
	} else {
		m.RunOnAddr(*bind)
	}
}
