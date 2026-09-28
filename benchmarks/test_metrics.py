import unittest
from metrics import quantiles


class MetricTests(unittest.TestCase):
    def test_continuous_percentiles_include_tail(self):
        self.assertEqual(quantiles([0, 100, 200, 300]),
                         {'count': 4, 'p50': 150, 'p95': 285, 'p99': 297, 'max': 300})

    def test_one_sample_and_empty_sample(self):
        self.assertEqual(quantiles([2])['p95'], 2)
        with self.assertRaises(ValueError):
            quantiles([])


if __name__ == '__main__':
    unittest.main()
