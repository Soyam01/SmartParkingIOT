import itertools
import sys
import unittest
from unittest.mock import MagicMock, Mock, patch

import servo_code


class ArrivalRecognitionTests(unittest.TestCase):
    def setUp(self):
        self.arrival = {
            'reservationId': 42,
            'plateNumber': 'BA12PA3456',
            'spotNumber': 'SPOT-3',
            'matchedAt': '2026-11-03T12:00:00',
        }

    @patch('servo_code.requests.post')
    def test_match_returns_assigned_spot_from_spring(self, post):
        post.return_value.status_code = 200
        post.return_value.json.return_value = self.arrival
        self.assertEqual(servo_code.match_arrival('BA12PA3456'), self.arrival)
        post.assert_called_once_with(
            f'{servo_code.SPRING_SERVER_URL}/api/arrivals/match',
            json={'plateNumber': 'BA12PA3456'},
            timeout=5,
        )

    @patch('servo_code.requests.post')
    def test_invalid_success_payload_cannot_authorize_gate(self, post):
        post.return_value.status_code = 200
        for payload in ([], {}, {**self.arrival, 'reservationId': True},
                        {**self.arrival, 'reservationId': 0},
                        {**self.arrival, 'spotNumber': 'SPOT-6'},
                        {**self.arrival, 'plateNumber': None}):
            with self.subTest(payload=payload):
                post.return_value.json.return_value = payload
                self.assertIsNone(servo_code.match_arrival('BA12PA3456'))

    @patch('servo_code.requests.post')
    def test_unregistered_blocked_and_unavailable_arrivals_fail_closed(self, post):
        for status in (404, 409, 500):
            with self.subTest(status=status):
                post.return_value.status_code = status
                post.return_value.json.return_value = {'message': 'Spot occupied'}
                self.assertIsNone(servo_code.match_arrival('BA12PA3456'))
        post.side_effect = servo_code.requests.exceptions.ConnectionError('Server unavailable')
        self.assertIsNone(servo_code.match_arrival('BA12PA3456'))

    @patch('servo_code.requests.post')
    def test_malformed_json_fails_closed(self, post):
        post.return_value.status_code = 200
        post.return_value.json.side_effect = ValueError('Invalid JSON')
        self.assertIsNone(servo_code.match_arrival('BA12PA3456'))

    def run_camera(self, gate_results, clock=None):
        cv2 = MagicMock()
        camera = cv2.VideoCapture.return_value
        camera.isOpened.return_value = True
        camera.read.side_effect = [(True, object())] * 3 + [(False, None)]
        cv2.waitKey.return_value = -1
        easyocr = MagicMock()
        easyocr.Reader.return_value.readtext.return_value = [([], 'BA 12-PA3456', .95)]
        response = Mock(status_code=200)
        response.json.return_value = self.arrival
        ticks = itertools.count(100, 10) if clock is None else itertools.repeat(clock)

        with patch.dict(sys.modules, {'cv2': cv2, 'easyocr': easyocr}), \
                patch('servo_code.time.monotonic', side_effect=lambda: next(ticks)), \
                patch('servo_code.requests.post', return_value=response) as post, \
                patch('servo_code.open_gate', side_effect=gate_results) as gate:
            self.assertEqual(servo_code.main(), 0)

        camera.release.assert_called_once()
        cv2.destroyAllWindows.assert_called_once()
        for call in post.call_args_list:
            self.assertEqual(call.kwargs['json']['plateNumber'], 'BA12PA3456')
        return post.call_count, gate.call_count

    def test_repeated_detection_opens_once_and_preserves_real_letters(self):
        self.assertEqual(self.run_camera([True]), (3, 1))

    def test_failed_gate_command_can_retry_same_held_reservation(self):
        self.assertEqual(self.run_camera([False, True]), (3, 2))

    def test_camera_frames_are_throttled_between_match_attempts(self):
        self.assertEqual(self.run_camera([True], clock=100), (1, 1))


if __name__ == '__main__':
    unittest.main()
