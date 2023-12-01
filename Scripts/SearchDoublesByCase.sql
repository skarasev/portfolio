SELECT *FROM
  (SELECT CASE
              WHEN substring(job_data, 774, 1) = '"' THEN substring(job_data, 775, 36)
              WHEN substring(job_data, 774, 1) != '"' THEN substring(job_data, 774, 36)
          END
   FROM qrtz_job_details
   WHERE job_data like '%{"Destination":"rabbitmq://haproxy.company.com/some_queue"%'
   LIMIT 1000000) AS jobs
GROUP BY jobs.CASE
HAVING COUNT(jobs.CASE) > 1