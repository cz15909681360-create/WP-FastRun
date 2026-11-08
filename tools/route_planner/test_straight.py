import unittest
from native_app import geometry
from main import csv_text, parse_csv, distance

class StraightTests(unittest.TestCase):
    def test_open_closed(self):
        p=[[114.328,30.511],[114.329,30.512],[114.33,30.51]]
        self.assertEqual(geometry(p,False),p)
        self.assertEqual(geometry(p,True),p+[p[0]])
        self.assertEqual(len(p),3)
        self.assertGreater(distance(geometry(p,True)),distance(p))
    def test_no_duplicate_closure(self):
        p=[[114,30],[115,31],[114,30]]
        self.assertEqual(geometry(p,True),p)
    def test_limit(self):
        p=[[114+i*.000001,30] for i in range(10000)]
        self.assertEqual(len(geometry(p,False)),10000)
        with self.assertRaises(ValueError):geometry(p,True)
    def test_csv(self):
        p=[[114.328,30.511],[114.329,30.512]]
        self.assertEqual(parse_csv(csv_text(geometry(p,True))),p+[p[0]])
    def test_invalid(self):
        for p in [[[float('nan'),30]],[[181,30]],[[114,91]]]:
            with self.assertRaises(ValueError):geometry(p,False)

if __name__=='__main__':unittest.main()
