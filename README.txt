

1. IdcDm
	- Processing of input parameters
	- Getting size of download file
	- Decide the download range of each connection
	- Creating the multi connection for downloading according to limit number of thread, download rate and download range
	- Detection of interrupt of program and notify it to all threads 

2. HTTPRangeGetter
	- Downloading the file according to range
	- When interrupt is occurred, store the download state to metadata file
	- Detection of network failure
	- Resume of downloading
	- Control download rate by Token Bucket algorithm

3. Range
	- Present the range for split downloading

4. TokenBucket
	- Implement the Token Bucket algorithm in order to control download rate
 
5. DownloadableMetadata
	- Present the Download metadata 
